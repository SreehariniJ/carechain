package com.carechain.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class CareChainMetricsService {

    private final MeterRegistry meterRegistry;

    public CareChainMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordTriageAssessment(String department, String triageLevel) {
        Counter.builder("carechain.triage.assessments")
                .description("Total AI-assisted symptom routing assessments")
                .tag("department", sanitizeTag(department))
                .tag("triage_level", sanitizeTag(triageLevel))
                .register(meterRegistry)
                .increment();
    }

    public void recordTriageOverride(String department, String triageLevel) {
        Counter.builder("carechain.triage.overrides")
                .description("Total human overrides applied to symptom routing decisions")
                .tag("department", sanitizeTag(department))
                .tag("triage_level", sanitizeTag(triageLevel))
                .register(meterRegistry)
                .increment();
    }

    public void recordDischargeSummarySaved(String wardType) {
        Counter.builder("carechain.discharge.summaries")
                .description("Total saved discharge summaries")
                .tag("ward_type", sanitizeTag(wardType))
                .register(meterRegistry)
                .increment();
    }

    public void recordDischargePdfGenerated(String wardType) {
        Counter.builder("carechain.discharge.pdf_exports")
                .description("Total discharge summary and billing PDFs generated")
                .tag("ward_type", sanitizeTag(wardType))
                .register(meterRegistry)
                .increment();
    }

    private String sanitizeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase().replace(' ', '_');
    }
}
