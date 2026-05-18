package com.carechain.triage;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TriageResponseMapper {

    private TriageResponseMapper() {
    }

    public static Map<String, Object> toPatientResponse(SymptomAssessment assessment) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", assessment.getId());
        response.put("submittedAt", assessment.getSubmittedAt().toString());
        response.put("symptoms", assessment.getSymptomText());
        response.put("suggestedDepartment", assessment.getSuggestedDepartment());
        response.put("suggestedTriageLevel", assessment.getTriageLevel().name());
        response.put("finalDepartment", assessment.getEffectiveDepartment());
        response.put("finalTriageLevel", assessment.getEffectiveTriageLevel().name());
        response.put("confidenceScore", assessment.getConfidenceScore());
        response.put("routingRationale", assessment.getRoutingRationale() == null ? "" : assessment.getRoutingRationale());
        response.put("matchedSignals", parseSignals(assessment.getMatchedSignals()));
        response.put("reviewStatus", assessment.getReviewStatus().name());
        response.put("reviewNote", assessment.getReviewNote() == null ? "" : assessment.getReviewNote());
        response.put("reviewedByEmail", assessment.getReviewedByEmail() == null ? "" : assessment.getReviewedByEmail());
        response.put("reviewedAt", assessment.getReviewedAt() == null ? "" : assessment.getReviewedAt().toString());
        return response;
    }

    public static Map<String, Object> toAdminResponse(SymptomAssessment assessment) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", assessment.getId());
        response.put("submittedAt", assessment.getSubmittedAt().toString());
        response.put("patientId", assessment.getPatient().getId());
        response.put("patientName", assessment.getPatient().getName() == null ? "N/A" : assessment.getPatient().getName());
        response.put("patientAge", assessment.getPatient().getAge() == null ? 0 : assessment.getPatient().getAge());
        response.put("patientEmail", assessment.getPatient().getUser().getEmail());
        response.put("symptoms", assessment.getSymptomText());
        response.put("suggestedDepartment", assessment.getSuggestedDepartment());
        response.put("suggestedTriageLevel", assessment.getTriageLevel().name());
        response.put("finalDepartment", assessment.getEffectiveDepartment());
        response.put("finalTriageLevel", assessment.getEffectiveTriageLevel().name());
        response.put("confidenceScore", assessment.getConfidenceScore());
        response.put("routingRationale", assessment.getRoutingRationale() == null ? "" : assessment.getRoutingRationale());
        response.put("matchedSignals", parseSignals(assessment.getMatchedSignals()));
        response.put("reviewStatus", assessment.getReviewStatus().name());
        response.put("reviewNote", assessment.getReviewNote() == null ? "" : assessment.getReviewNote());
        response.put("reviewedByEmail", assessment.getReviewedByEmail() == null ? "" : assessment.getReviewedByEmail());
        response.put("reviewedAt", assessment.getReviewedAt() == null ? "" : assessment.getReviewedAt().toString());
        return response;
    }

    private static List<String> parseSignals(String matchedSignals) {
        if (matchedSignals == null || matchedSignals.isBlank()) {
            return List.of();
        }
        return Arrays.stream(matchedSignals.split("\\s*,\\s*"))
                .filter(value -> !value.isBlank())
                .toList();
    }
}
