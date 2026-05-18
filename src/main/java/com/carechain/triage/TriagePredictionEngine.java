package com.carechain.triage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TriagePredictionEngine {

    private final TriageFeatureExtractor featureExtractor;
    private final TriageModelProperties properties;

    public TriagePredictionEngine(TriageFeatureExtractor featureExtractor, TriageModelProperties properties) {
        this.featureExtractor = featureExtractor;
        this.properties = properties;
    }

    public TriagePrediction predict(TriageModelSnapshot modelSnapshot, Integer patientAge, String symptoms) {
        String normalized = featureExtractor.canonicalize(symptoms);
        Map<String, Double> inputVector = featureExtractor.buildInputVector(normalized, modelSnapshot.inverseDocumentFrequency());
        List<RankedExample> rankedExamples = rankExamples(modelSnapshot, inputVector);

        boolean emergencyRuleTriggered = featureExtractor.containsEmergencyPattern(normalized);
        boolean emergencyModelTriggered = rankedExamples.stream()
                .limit(2)
                .anyMatch(match -> "Emergency".equals(match.example().department())
                        && match.similarity() >= properties.getEmergencySimilarityThreshold());
        boolean redFlag = emergencyRuleTriggered || emergencyModelTriggered;
        int urgentSignals = featureExtractor.countPhraseMatches(normalized, featureExtractor.highUrgencyPhrases());

        Map<String, Double> departmentVotes = new LinkedHashMap<>();
        EnumMap<TriageLevel, Double> triageVotes = new EnumMap<>(TriageLevel.class);
        for (RankedExample match : rankedExamples) {
            double voteWeight = match.similarity();
            departmentVotes.merge(match.example().department(), voteWeight, Double::sum);
            triageVotes.merge(match.example().triageLevel(), voteWeight, Double::sum);
        }

        boolean pediatricBoostApplied = patientAge != null && patientAge < properties.getPediatricAgeCutoff();
        if (pediatricBoostApplied) {
            double pediatricBoost = featureExtractor.hasPediatricContext(normalized)
                    ? properties.getPediatricContextBoost()
                    : properties.getPediatricBoost();
            departmentVotes.merge("Pediatrics", pediatricBoost, Double::sum);
        }

        DepartmentDecision departmentDecision = chooseDepartment(departmentVotes, rankedExamples, redFlag);
        TriageLevel triageLevel = chooseTriageLevel(triageVotes, urgentSignals, departmentDecision.department(), redFlag);
        List<String> matchedSignals = collectMatchedSignals(normalized, rankedExamples, pediatricBoostApplied, redFlag);
        if (matchedSignals.isEmpty()) {
            matchedSignals = List.of("broad primary-care symptoms");
        }

        BigDecimal confidence = resolveConfidence(
                rankedExamples,
                departmentDecision.margin(),
                matchedSignals.size(),
                redFlag,
                departmentDecision.weakMatch()
        );
        String rationale = buildRationale(
                departmentDecision.department(),
                triageLevel,
                rankedExamples,
                matchedSignals,
                redFlag,
                departmentDecision.weakMatch(),
                pediatricBoostApplied
        );

        return new TriagePrediction(
                new TriageRecommendation(
                        departmentDecision.department(),
                        triageLevel,
                        confidence,
                        rationale,
                        matchedSignals
                ),
                departmentDecision.weakMatch(),
                redFlag
        );
    }

    private List<RankedExample> rankExamples(TriageModelSnapshot modelSnapshot, Map<String, Double> inputVector) {
        return modelSnapshot.examples().stream()
                .map(example -> new RankedExample(example, featureExtractor.cosineSimilarity(inputVector, example.vector())))
                .filter(match -> match.similarity() > properties.getMinimumSimilarity())
                .sorted(Comparator.comparingDouble(RankedExample::similarity).reversed())
                .limit(Math.max(1, properties.getNeighborCount()))
                .toList();
    }

    private DepartmentDecision chooseDepartment(Map<String, Double> departmentVotes,
                                                List<RankedExample> rankedExamples,
                                                boolean redFlag) {
        if (redFlag) {
            return new DepartmentDecision("Emergency", 1.0, false);
        }

        Map.Entry<String, Double> best = null;
        Map.Entry<String, Double> second = null;
        for (Map.Entry<String, Double> entry : departmentVotes.entrySet()) {
            if (best == null || entry.getValue() > best.getValue()) {
                second = best;
                best = entry;
            } else if (second == null || entry.getValue() > second.getValue()) {
                second = entry;
            }
        }

        double topSimilarity = rankedExamples.isEmpty() ? 0.0 : rankedExamples.get(0).similarity();
        boolean weakMatch = topSimilarity < properties.getWeakMatchThreshold();
        if (best == null || weakMatch) {
            double margin = best == null ? 0.0 : best.getValue() - (second == null ? 0.0 : second.getValue());
            return new DepartmentDecision("General Medicine", margin, true);
        }

        double margin = best.getValue() - (second == null ? 0.0 : second.getValue());
        return new DepartmentDecision(best.getKey(), margin, false);
    }

    private TriageLevel chooseTriageLevel(EnumMap<TriageLevel, Double> triageVotes,
                                          int urgentSignals,
                                          String department,
                                          boolean redFlag) {
        if (redFlag) {
            return TriageLevel.RED;
        }

        TriageLevel bestLevel = triageVotes.entrySet().stream()
                .max((left, right) -> {
                    int scoreComparison = Double.compare(left.getValue(), right.getValue());
                    if (scoreComparison != 0) {
                        return scoreComparison;
                    }
                    return Integer.compare(severityRank(right.getKey()), severityRank(left.getKey()));
                })
                .map(Map.Entry::getKey)
                .orElse(TriageLevel.GREEN);

        if (urgentSignals >= 2) {
            bestLevel = moreUrgent(bestLevel, TriageLevel.ORANGE);
        } else if (urgentSignals == 1) {
            bestLevel = moreUrgent(bestLevel, TriageLevel.YELLOW);
        }

        if ("Emergency".equals(department)) {
            bestLevel = moreUrgent(bestLevel, TriageLevel.ORANGE);
        }
        return bestLevel;
    }

    private BigDecimal resolveConfidence(List<RankedExample> rankedExamples,
                                         double departmentMargin,
                                         int matchedSignals,
                                         boolean redFlag,
                                         boolean weakMatch) {
        double topSimilarity = rankedExamples.isEmpty() ? 0.0 : rankedExamples.get(0).similarity();
        double secondSimilarity = rankedExamples.size() > 1 ? rankedExamples.get(1).similarity() : 0.0;
        double averageTopSimilarity = rankedExamples.stream()
                .limit(3)
                .mapToDouble(RankedExample::similarity)
                .average()
                .orElse(0.0);

        double confidence = 0.50;
        confidence += Math.min(0.20, topSimilarity * 0.45);
        confidence += Math.min(0.10, Math.max(0.0, topSimilarity - secondSimilarity) * 0.30);
        confidence += Math.min(0.07, Math.max(0.0, departmentMargin) * 0.20);
        confidence += Math.min(0.08, matchedSignals * 0.015);
        confidence += Math.min(0.05, averageTopSimilarity * 0.20);

        if (redFlag) {
            confidence = Math.max(confidence, 0.90);
        }
        if (weakMatch) {
            confidence = Math.min(confidence, 0.62);
        }

        return BigDecimal.valueOf(Math.max(0.51, Math.min(0.97, confidence)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String buildRationale(String department,
                                  TriageLevel triageLevel,
                                  List<RankedExample> rankedExamples,
                                  List<String> matchedSignals,
                                  boolean redFlag,
                                  boolean weakMatch,
                                  boolean pediatricBoostApplied) {
        String signalPreview = matchedSignals.stream().limit(3).collect(Collectors.joining(", "));
        if (redFlag) {
            return "The local triage model detected emergency symptom patterns such as " + signalPreview
                    + ", so the case was escalated for immediate review.";
        }
        if (weakMatch) {
            return "The local triage model found only weak similarity to its known symptom patterns, so the case was routed to General Medicine for broad clinical review.";
        }

        String closestPatterns = rankedExamples.stream()
                .limit(2)
                .map(match -> match.example().department())
                .distinct()
                .collect(Collectors.joining(" and "));
        String pediatricNote = pediatricBoostApplied && "Pediatrics".equals(department)
                ? " Age-specific pediatric context also increased the confidence of this route."
                : "";

        if (triageLevel == TriageLevel.ORANGE || triageLevel == TriageLevel.RED) {
            return "The local triage model matched this description most strongly with " + closestPatterns
                    + " cases and identified urgent patterns such as " + signalPreview + "." + pediatricNote;
        }
        return "The local triage model matched this description most strongly with " + closestPatterns
                + " cases, favoring " + department + " based on signals such as " + signalPreview + "." + pediatricNote;
    }

    private List<String> collectMatchedSignals(String normalized,
                                               List<RankedExample> rankedExamples,
                                               boolean pediatricBoostApplied,
                                               boolean redFlag) {
        Map<String, Double> signalWeights = new LinkedHashMap<>();
        for (RankedExample match : rankedExamples.stream().limit(3).toList()) {
            for (String phrase : match.example().phrases()) {
                if (normalized.contains(phrase) && featureExtractor.isDisplayableSignal(phrase)) {
                    double phraseWeight = match.similarity() + (phrase.split(" ").length * 0.03);
                    signalWeights.merge(phrase, phraseWeight, Double::sum);
                }
            }
        }

        for (String phrase : featureExtractor.redFlagPhrases()) {
            if (normalized.contains(phrase)) {
                signalWeights.merge(phrase, 2.0, Double::sum);
            }
        }
        for (String phrase : featureExtractor.highUrgencyPhrases()) {
            if (normalized.contains(phrase)) {
                signalWeights.merge(phrase, 1.2, Double::sum);
            }
        }
        if (pediatricBoostApplied) {
            signalWeights.merge("patient age under " + properties.getPediatricAgeCutoff(), 0.9, Double::sum);
        }
        if (redFlag) {
            signalWeights.merge("emergency review", 0.4, Double::sum);
        }

        return signalWeights.entrySet().stream()
                .sorted((left, right) -> {
                    int weightComparison = Double.compare(right.getValue(), left.getValue());
                    if (weightComparison != 0) {
                        return weightComparison;
                    }
                    return Integer.compare(right.getKey().length(), left.getKey().length());
                })
                .map(Map.Entry::getKey)
                .limit(8)
                .toList();
    }

    private TriageLevel moreUrgent(TriageLevel left, TriageLevel right) {
        return severityRank(left) <= severityRank(right) ? left : right;
    }

    private int severityRank(TriageLevel level) {
        return switch (level) {
            case RED -> 0;
            case ORANGE -> 1;
            case YELLOW -> 2;
            case GREEN -> 3;
        };
    }

    private record RankedExample(TriageModelExample example, double similarity) {
    }

    private record DepartmentDecision(String department, double margin, boolean weakMatch) {
    }
}

record TriagePrediction(
        TriageRecommendation recommendation,
        boolean weakMatch,
        boolean redFlag
) {
}
