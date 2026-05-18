package com.carechain.triage;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record TriageModelSnapshot(
        String modelVersion,
        String corpusLabel,
        Instant trainedAt,
        List<TriageModelExample> examples,
        Map<String, Double> inverseDocumentFrequency,
        int featureCount,
        Map<String, Integer> departmentDistribution,
        Map<String, Integer> triageDistribution
) {
}

record TriageModelExample(
        String department,
        TriageLevel triageLevel,
        String symptoms,
        String normalizedSymptoms,
        Set<String> phrases,
        Map<String, Double> vector,
        boolean pediatricExample
) {
}
