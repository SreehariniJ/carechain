package com.carechain.triage;

import java.time.Instant;
import java.util.Map;

public record TriageModelReport(
        String modelVersion,
        String corpusLabel,
        Instant trainedAt,
        int exampleCount,
        int featureCount,
        int neighborCount,
        double weakMatchThreshold,
        double emergencySimilarityThreshold,
        Map<String, Integer> departmentDistribution,
        Map<String, Integer> triageDistribution,
        TriageModelEvaluation evaluation
) {
}
