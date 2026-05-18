package com.carechain.triage;

public record TriageModelState(
        TriageModelSnapshot snapshot,
        TriageModelReport report
) {
}
