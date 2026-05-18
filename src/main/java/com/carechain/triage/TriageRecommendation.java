package com.carechain.triage;

import java.math.BigDecimal;
import java.util.List;

public record TriageRecommendation(
        String department,
        TriageLevel triageLevel,
        BigDecimal confidenceScore,
        String routingRationale,
        List<String> matchedSignals
) {
}
