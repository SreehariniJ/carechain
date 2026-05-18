package com.carechain.triage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TriageFeatureExtractor {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "been", "but", "by", "for", "from",
            "had", "has", "have", "he", "her", "his", "i", "if", "in", "into", "is", "it",
            "its", "me", "my", "of", "on", "or", "our", "she", "since", "that", "the",
            "their", "them", "there", "they", "this", "to", "was", "were", "with", "would",
            "you", "your"
    );

    private static final List<Map.Entry<String, String>> CANONICAL_REPLACEMENTS = List.of(
            Map.entry("cant breathe", "unable to breathe"),
            Map.entry("cannot breathe", "unable to breathe"),
            Map.entry("difficulty breathing", "shortness of breath"),
            Map.entry("trouble breathing", "shortness of breath"),
            Map.entry("breathless", "shortness of breath"),
            Map.entry("breathlessness", "shortness of breath"),
            Map.entry("short of breath", "shortness of breath"),
            Map.entry("passed out", "loss of consciousness"),
            Map.entry("fainted", "loss of consciousness"),
            Map.entry("collapsed", "loss of consciousness"),
            Map.entry("one side", "one sided"),
            Map.entry("facial drooping", "facial droop"),
            Map.entry("throwing up", "vomiting"),
            Map.entry("throw up", "vomiting"),
            Map.entry("loose stools", "diarrhea"),
            Map.entry("loose motions", "diarrhea"),
            Map.entry("kid ", "child "),
            Map.entry("kids ", "children "),
            Map.entry("tummy pain", "abdominal pain")
    );

    private static final List<String> RED_FLAG_PHRASES = List.of(
            "unable to breathe",
            "severe chest pain",
            "loss of consciousness",
            "severe bleeding",
            "one sided weakness",
            "slurred speech",
            "facial droop",
            "seizure",
            "unresponsive",
            "stroke symptoms"
    );

    private static final List<String> HIGH_URGENCY_PHRASES = List.of(
            "shortness of breath",
            "fracture",
            "persistent vomiting",
            "high fever",
            "severe pain",
            "dehydration",
            "rapid swelling",
            "confusion",
            "loss of consciousness",
            "unable to bear weight"
    );

    private static final List<String> PEDIATRIC_CONTEXT_PHRASES = List.of(
            "child",
            "children",
            "baby",
            "infant",
            "newborn",
            "vaccination",
            "feeding"
    );

    public String canonicalize(String symptoms) {
        String normalized = symptoms == null
                ? ""
                : symptoms.toLowerCase(Locale.ROOT)
                .replace("\u2019", "'")
                .replace("'", "")
                .replace('-', ' ')
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();

        for (Map.Entry<String, String> replacement : CANONICAL_REPLACEMENTS) {
            normalized = normalized.replace(replacement.getKey(), replacement.getValue());
        }
        return normalized.replaceAll("\\s{2,}", " ").trim();
    }

    public Map<String, Double> extractFeatureWeights(String normalized) {
        Map<String, Double> termWeights = new LinkedHashMap<>();
        List<String> tokens = tokenize(normalized);
        for (String token : tokens) {
            if (!STOP_WORDS.contains(token) && token.length() > 2) {
                termWeights.merge(token, 1.0, Double::sum);
            }
        }
        addNgrams(tokens, termWeights, 2, 1.3);
        addNgrams(tokens, termWeights, 3, 1.5);
        return termWeights;
    }

    public Set<String> extractPhraseSet(String normalized) {
        LinkedHashSet<String> phrases = new LinkedHashSet<>();
        List<String> tokens = tokenize(normalized);
        for (String token : tokens) {
            if (!STOP_WORDS.contains(token) && token.length() > 3) {
                phrases.add(token);
            }
        }
        collectDisplayPhrases(tokens, phrases, 2);
        collectDisplayPhrases(tokens, phrases, 3);
        return phrases;
    }

    public Map<String, Double> buildInputVector(String normalized, Map<String, Double> inverseDocumentFrequency) {
        return vectorize(extractFeatureWeights(normalized), inverseDocumentFrequency);
    }

    public Map<String, Double> vectorize(Map<String, Double> termWeights, Map<String, Double> inverseDocumentFrequency) {
        Map<String, Double> vector = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : termWeights.entrySet()) {
            double idf = inverseDocumentFrequency.getOrDefault(entry.getKey(), 1.0);
            vector.put(entry.getKey(), entry.getValue() * idf);
        }
        normalizeVector(vector);
        return Map.copyOf(vector);
    }

    public double cosineSimilarity(Map<String, Double> left, Map<String, Double> right) {
        Map<String, Double> smaller = left.size() <= right.size() ? left : right;
        Map<String, Double> larger = smaller == left ? right : left;
        double dotProduct = 0.0;
        for (Map.Entry<String, Double> entry : smaller.entrySet()) {
            dotProduct += entry.getValue() * larger.getOrDefault(entry.getKey(), 0.0);
        }
        return dotProduct;
    }

    public boolean containsEmergencyPattern(String normalized) {
        boolean cardioDistress = (normalized.contains("chest pain") || normalized.contains("severe chest pain"))
                && (normalized.contains("shortness of breath") || normalized.contains("unable to breathe"));
        boolean neuroEmergency = normalized.contains("slurred speech")
                || normalized.contains("one sided weakness")
                || normalized.contains("facial droop");
        return cardioDistress || neuroEmergency || containsAnyPhrase(normalized, RED_FLAG_PHRASES);
    }

    public boolean containsAnyPhrase(String normalized, List<String> phrases) {
        return phrases.stream().anyMatch(normalized::contains);
    }

    public int countPhraseMatches(String normalized, List<String> phrases) {
        int matches = 0;
        for (String phrase : phrases) {
            if (normalized.contains(phrase)) {
                matches++;
            }
        }
        return matches;
    }

    public boolean hasPediatricContext(String normalized) {
        return containsAnyPhrase(normalized, PEDIATRIC_CONTEXT_PHRASES);
    }

    public boolean isDisplayableSignal(String phrase) {
        return phrase.length() >= 4
                && phrase.split(" ").length <= 4
                && !STOP_WORDS.contains(phrase);
    }

    public List<String> redFlagPhrases() {
        return RED_FLAG_PHRASES;
    }

    public List<String> highUrgencyPhrases() {
        return HIGH_URGENCY_PHRASES;
    }

    private void normalizeVector(Map<String, Double> vector) {
        double magnitude = Math.sqrt(vector.values().stream()
                .mapToDouble(weight -> weight * weight)
                .sum());
        if (magnitude == 0.0) {
            return;
        }
        for (Map.Entry<String, Double> entry : new ArrayList<>(vector.entrySet())) {
            vector.put(entry.getKey(), entry.getValue() / magnitude);
        }
    }

    private void addNgrams(List<String> tokens, Map<String, Double> termWeights, int length, double weight) {
        if (tokens.size() < length) {
            return;
        }
        for (int index = 0; index <= tokens.size() - length; index++) {
            List<String> window = tokens.subList(index, index + length);
            if (isUsefulPhrase(window)) {
                termWeights.merge(String.join(" ", window), weight, Double::sum);
            }
        }
    }

    private void collectDisplayPhrases(List<String> tokens, Set<String> phrases, int length) {
        if (tokens.size() < length) {
            return;
        }
        for (int index = 0; index <= tokens.size() - length; index++) {
            List<String> window = tokens.subList(index, index + length);
            if (isUsefulPhrase(window)) {
                phrases.add(String.join(" ", window));
            }
        }
    }

    private boolean isUsefulPhrase(List<String> window) {
        boolean hasMeaningfulToken = window.stream().anyMatch(token -> !STOP_WORDS.contains(token) && token.length() > 2);
        int totalCharacters = window.stream().mapToInt(String::length).sum();
        return hasMeaningfulToken && totalCharacters >= 5;
    }

    private List<String> tokenize(String normalized) {
        if (normalized.isBlank()) {
            return List.of();
        }
        return Arrays.stream(normalized.split(" "))
                .filter(token -> !token.isBlank())
                .toList();
    }
}
