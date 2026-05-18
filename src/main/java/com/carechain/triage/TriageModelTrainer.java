package com.carechain.triage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TriageModelTrainer {

    private final TriageFeatureExtractor featureExtractor = new TriageFeatureExtractor();

    public TriageModelState trainFromResource(TriageModelProperties properties) {
        return trainFromResource(properties, properties.getVersion(), properties.getCorpusResource());
    }

    public TriageModelState trainFromResource(TriageModelProperties properties,
                                              String requestedModelVersion,
                                              String requestedCorpusLabel) {
        List<ParsedExample> parsedExamples = loadTrainingCorpus(properties.getCorpusResource());
        return train(
                properties,
                resolveModelVersion(requestedModelVersion, properties),
                resolveCorpusLabel(requestedCorpusLabel, properties.getCorpusResource()),
                parsedExamples
        );
    }

    public TriageModelState trainFromExamples(TriageModelProperties properties,
                                              String requestedModelVersion,
                                              String requestedCorpusLabel,
                                              List<TriageTrainingExampleRequest> examples) {
        if (examples == null || examples.isEmpty()) {
            throw new IllegalArgumentException("Provide at least 4 labeled examples to retrain the triage model");
        }

        List<ParsedExample> parsedExamples = examples.stream()
                .map(this::parseRequestExample)
                .toList();

        return train(
                properties,
                resolveModelVersion(requestedModelVersion, properties),
                resolveCorpusLabel(requestedCorpusLabel, "runtime-upload"),
                parsedExamples
        );
    }

    private TriageModelState train(TriageModelProperties properties,
                                   String modelVersion,
                                   String corpusLabel,
                                   List<ParsedExample> parsedExamples) {
        validateTrainingSet(parsedExamples);

        Instant trainedAt = Instant.now();
        TriageModelSnapshot snapshot = buildSnapshot(modelVersion, corpusLabel, trainedAt, parsedExamples);
        TriageModelEvaluation evaluation = evaluateModel(snapshot, parsedExamples, properties);
        TriageModelReport report = new TriageModelReport(
                snapshot.modelVersion(),
                snapshot.corpusLabel(),
                snapshot.trainedAt(),
                snapshot.examples().size(),
                snapshot.featureCount(),
                Math.max(1, properties.getNeighborCount()),
                properties.getWeakMatchThreshold(),
                properties.getEmergencySimilarityThreshold(),
                snapshot.departmentDistribution(),
                snapshot.triageDistribution(),
                evaluation
        );
        return new TriageModelState(snapshot, report);
    }

    private TriageModelSnapshot buildSnapshot(String modelVersion,
                                              String corpusLabel,
                                              Instant trainedAt,
                                              List<ParsedExample> parsedExamples) {
        Map<String, Double> inverseDocumentFrequency = computeInverseDocumentFrequency(parsedExamples);
        List<TriageModelExample> examples = parsedExamples.stream()
                .map(example -> new TriageModelExample(
                        example.department(),
                        example.triageLevel(),
                        example.symptoms(),
                        example.normalizedSymptoms(),
                        Set.copyOf(example.phrases()),
                        featureExtractor.vectorize(example.termWeights(), inverseDocumentFrequency),
                        example.pediatricExample()))
                .toList();

        return new TriageModelSnapshot(
                modelVersion,
                corpusLabel,
                trainedAt,
                List.copyOf(examples),
                Map.copyOf(inverseDocumentFrequency),
                inverseDocumentFrequency.size(),
                Map.copyOf(buildDepartmentDistribution(parsedExamples)),
                Map.copyOf(buildTriageDistribution(parsedExamples))
        );
    }

    private TriageModelEvaluation evaluateModel(TriageModelSnapshot snapshot,
                                                List<ParsedExample> parsedExamples,
                                                TriageModelProperties properties) {
        TriagePredictionEngine predictionEngine = new TriagePredictionEngine(featureExtractor, properties);
        int departmentMatches = 0;
        int triageMatches = 0;
        int exactMatches = 0;
        int weakMatches = 0;
        List<String> notableMismatches = new ArrayList<>();

        for (int index = 0; index < parsedExamples.size(); index++) {
            ParsedExample heldOut = parsedExamples.get(index);
            List<ParsedExample> foldExamples = new ArrayList<>(parsedExamples);
            foldExamples.remove(index);

            TriageModelSnapshot foldSnapshot = buildSnapshot(
                    snapshot.modelVersion(),
                    snapshot.corpusLabel(),
                    snapshot.trainedAt(),
                    foldExamples
            );
            Integer patientAge = heldOut.pediatricExample()
                    ? Math.max(1, properties.getPediatricAgeCutoff() - 1)
                    : properties.getPediatricAgeCutoff() + 18;
            TriagePrediction prediction = predictionEngine.predict(foldSnapshot, patientAge, heldOut.symptoms());

            boolean departmentMatch = prediction.recommendation().department().equals(heldOut.department());
            boolean triageMatch = prediction.recommendation().triageLevel() == heldOut.triageLevel();
            if (departmentMatch) {
                departmentMatches++;
            }
            if (triageMatch) {
                triageMatches++;
            }
            if (departmentMatch && triageMatch) {
                exactMatches++;
            }
            if (prediction.weakMatch()) {
                weakMatches++;
            }
            if ((!departmentMatch || !triageMatch) && notableMismatches.size() < 5) {
                notableMismatches.add("\"" + heldOut.symptoms() + "\" expected "
                        + heldOut.department() + "/" + heldOut.triageLevel().name()
                        + " but predicted "
                        + prediction.recommendation().department() + "/"
                        + prediction.recommendation().triageLevel().name());
            }
        }

        int sampleCount = parsedExamples.size();
        return new TriageModelEvaluation(
                sampleCount,
                departmentMatches,
                triageMatches,
                exactMatches,
                weakMatches,
                ratio(departmentMatches, sampleCount),
                ratio(triageMatches, sampleCount),
                ratio(exactMatches, sampleCount),
                List.copyOf(notableMismatches)
        );
    }

    private List<ParsedExample> loadTrainingCorpus(String resourcePath) {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IllegalStateException("Triage training corpus is missing: " + resourcePath);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .filter(line -> !line.startsWith("#"))
                    .map(this::parseTrainingLine)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load triage training corpus", exception);
        }
    }

    private ParsedExample parseTrainingLine(String line) {
        String[] segments = line.split("\\|", 3);
        if (segments.length != 3) {
            throw new IllegalStateException("Invalid triage training row: " + line);
        }

        String department = segments[0].trim();
        TriageLevel triageLevel = TriageLevel.valueOf(segments[1].trim().toUpperCase(Locale.ROOT));
        return buildParsedExample(department, triageLevel, segments[2].trim());
    }

    private ParsedExample parseRequestExample(TriageTrainingExampleRequest request) {
        return buildParsedExample(request.getDepartment(), request.getTriageLevel(), request.getSymptoms());
    }

    private ParsedExample buildParsedExample(String department, TriageLevel triageLevel, String symptoms) {
        String normalizedDepartment = normalizeValue(department);
        if (normalizedDepartment.isBlank()) {
            throw new IllegalArgumentException("Department is required for every training example");
        }
        if (triageLevel == null) {
            throw new IllegalArgumentException("Triage level is required for every training example");
        }
        String normalizedSymptoms = featureExtractor.canonicalize(symptoms);
        if (normalizedSymptoms.isBlank()) {
            throw new IllegalArgumentException("Symptoms are required for every training example");
        }

        return new ParsedExample(
                normalizedDepartment,
                triageLevel,
                normalizeValue(symptoms),
                normalizedSymptoms,
                featureExtractor.extractFeatureWeights(normalizedSymptoms),
                featureExtractor.extractPhraseSet(normalizedSymptoms),
                "Pediatrics".equals(normalizedDepartment) || featureExtractor.hasPediatricContext(normalizedSymptoms)
        );
    }

    private void validateTrainingSet(List<ParsedExample> parsedExamples) {
        if (parsedExamples.size() < 4) {
            throw new IllegalArgumentException("Triage model retraining needs at least 4 labeled examples");
        }

        long distinctDepartments = parsedExamples.stream()
                .map(ParsedExample::department)
                .distinct()
                .count();
        if (distinctDepartments < 2) {
            throw new IllegalArgumentException("Triage model retraining needs examples from at least 2 departments");
        }
    }

    private Map<String, Double> computeInverseDocumentFrequency(List<ParsedExample> examples) {
        Map<String, Integer> documentFrequency = new LinkedHashMap<>();
        for (ParsedExample example : examples) {
            for (String term : example.termWeights().keySet()) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }

        int documentCount = Math.max(1, examples.size());
        Map<String, Double> idf = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : documentFrequency.entrySet()) {
            double weight = Math.log((1.0 + documentCount) / (1.0 + entry.getValue())) + 1.0;
            idf.put(entry.getKey(), weight);
        }
        return idf;
    }

    private Map<String, Integer> buildDepartmentDistribution(List<ParsedExample> parsedExamples) {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        for (ParsedExample example : parsedExamples) {
            distribution.merge(example.department(), 1, Integer::sum);
        }
        return distribution;
    }

    private Map<String, Integer> buildTriageDistribution(List<ParsedExample> parsedExamples) {
        EnumMap<TriageLevel, Integer> counts = new EnumMap<>(TriageLevel.class);
        for (ParsedExample example : parsedExamples) {
            counts.merge(example.triageLevel(), 1, Integer::sum);
        }

        Map<String, Integer> distribution = new LinkedHashMap<>();
        for (TriageLevel level : TriageLevel.values()) {
            if (counts.containsKey(level)) {
                distribution.put(level.name(), counts.get(level));
            }
        }
        return distribution;
    }

    private BigDecimal ratio(int numerator, int denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf((double) numerator / denominator)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String resolveModelVersion(String requestedModelVersion, TriageModelProperties properties) {
        String normalized = normalizeValue(requestedModelVersion);
        return normalized.isBlank() ? properties.getVersion() : normalized;
    }

    private String resolveCorpusLabel(String requestedCorpusLabel, String defaultLabel) {
        String normalized = normalizeValue(requestedCorpusLabel);
        return normalized.isBlank() ? defaultLabel : normalized;
    }

    private String normalizeValue(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s{2,}", " ");
    }

    private record ParsedExample(
            String department,
            TriageLevel triageLevel,
            String symptoms,
            String normalizedSymptoms,
            Map<String, Double> termWeights,
            Set<String> phrases,
            boolean pediatricExample
    ) {
    }
}
