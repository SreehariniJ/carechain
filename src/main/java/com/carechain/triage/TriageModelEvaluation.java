package com.carechain.triage;

import java.math.BigDecimal;
import java.util.List;

public record TriageModelEvaluation(
        int sampleCount,
        int departmentMatches,
        int triageMatches,
        int exactMatches,
        int weakMatches,
        BigDecimal departmentAccuracy,
        BigDecimal triageAccuracy,
        BigDecimal exactMatchAccuracy,
        List<String> notableMismatches
) {
}
