package com.carechain.triage;

import com.carechain.audit.AuditEvent;
import com.carechain.audit.AuditMetadata;
import com.carechain.audit.AuditTrailService;
import com.carechain.config.ApiErrorException;
import com.carechain.metrics.CareChainMetricsService;
import com.carechain.patient.PatientService;
import com.carechain.patient.model.Patient;
import com.carechain.realtime.RealtimeNotifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Service
public class TriageService {

    private final SymptomAssessmentRepository symptomAssessmentRepository;
    private final SymptomRouterService symptomRouterService;
    private final PatientService patientService;
    private final RealtimeNotifier realtimeNotifier;
    private final AuditTrailService auditTrailService;
    private final CareChainMetricsService metricsService;

    public TriageService(SymptomAssessmentRepository symptomAssessmentRepository,
                         SymptomRouterService symptomRouterService,
                         PatientService patientService,
                         RealtimeNotifier realtimeNotifier,
                         AuditTrailService auditTrailService,
                         CareChainMetricsService metricsService) {
        this.symptomAssessmentRepository = symptomAssessmentRepository;
        this.symptomRouterService = symptomRouterService;
        this.patientService = patientService;
        this.realtimeNotifier = realtimeNotifier;
        this.auditTrailService = auditTrailService;
        this.metricsService = metricsService;
    }

    @Transactional
    @PreAuthorize("hasRole('PATIENT')")
    public SymptomAssessment submitAssessment(String email, SymptomAssessmentRequest request) {
        Patient patient = patientService.getPatientByEmail(email);
        TriageRecommendation recommendation = symptomRouterService.route(patient, request.getSymptoms());

        SymptomAssessment assessment = SymptomAssessment.builder()
                .patient(patient)
                .symptomText(normalizeText(request.getSymptoms()))
                .suggestedDepartment(recommendation.department())
                .triageLevel(recommendation.triageLevel())
                .confidenceScore(recommendation.confidenceScore())
                .routingRationale(recommendation.routingRationale())
                .matchedSignals(String.join(", ", recommendation.matchedSignals()))
                .reviewStatus(needsReview(recommendation) ? TriageReviewStatus.PENDING_REVIEW : TriageReviewStatus.AUTO_APPROVED)
                .build();

        SymptomAssessment savedAssessment = symptomAssessmentRepository.saveAndFlush(assessment);
        realtimeNotifier.publishAdminRefresh("triage-submitted");
        realtimeNotifier.publishPatientTriageRefresh(
                email,
                "triage-submitted",
                "AI triage recommended " + savedAssessment.getSuggestedDepartment() + " with " + savedAssessment.getTriageLevel().name() + " priority.",
                toastLevel(savedAssessment.getTriageLevel()));
        metricsService.recordTriageAssessment(savedAssessment.getSuggestedDepartment(), savedAssessment.getTriageLevel().name());
        auditTrailService.record(AuditEvent.builder()
                .action("TRIAGE_SUBMITTED")
                .resourceType("TRIAGE")
                .resourceId(AuditMetadata.id(savedAssessment.getId()))
                .details(AuditMetadata.map(
                        "patientId", patient.getId(),
                        "suggestedDepartment", savedAssessment.getSuggestedDepartment(),
                        "triageLevel", savedAssessment.getTriageLevel().name(),
                        "confidenceScore", savedAssessment.getConfidenceScore(),
                        "reviewStatus", savedAssessment.getReviewStatus().name()))
                .build());
        return savedAssessment;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('PATIENT')")
    public List<SymptomAssessment> getPatientAssessments(String email) {
        Patient patient = patientService.getPatientByEmail(email);
        return symptomAssessmentRepository.findHistoryForPatient(patient.getId());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<SymptomAssessment> getRecentAssessments(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return symptomAssessmentRepository.findRecentAssessments(PageRequest.of(0, safeLimit)).getContent();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public TriageModelReport getModelReport() {
        return symptomRouterService.getModelReport();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public TriageModelReport retrainModel(TriageModelRetrainRequest request, String reviewerEmail) {
        TriageModelReport report = symptomRouterService.retrain(request);
        realtimeNotifier.publishAdminRefresh("triage-model-retrained");
        auditTrailService.record(AuditEvent.builder()
                .action("TRIAGE_MODEL_RETRAINED")
                .resourceType("TRIAGE_MODEL")
                .resourceId(AuditMetadata.id(report.modelVersion()))
                .details(AuditMetadata.map(
                        "modelVersion", report.modelVersion(),
                        "corpusLabel", report.corpusLabel(),
                        "exampleCount", report.exampleCount(),
                        "departmentAccuracy", report.evaluation().departmentAccuracy(),
                        "triageAccuracy", report.evaluation().triageAccuracy(),
                        "exactMatchAccuracy", report.evaluation().exactMatchAccuracy(),
                        "triggeredByEmail", reviewerEmail.toLowerCase(Locale.ROOT)))
                .build());
        return report;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or hasRole('DOCTOR')")
    public SymptomAssessment overrideAssessment(Long assessmentId,
                                                TriageOverrideRequest request,
                                                String reviewerEmail) {
        SymptomAssessment assessment = symptomAssessmentRepository.findDetailedById(assessmentId)
                .orElseThrow(() -> ApiErrorException.notFound("Triage assessment not found"));

        assessment.setReviewedDepartment(normalizeDepartment(request.getDepartment()));
        assessment.setReviewedTriageLevel(request.getTriageLevel());
        assessment.setReviewNote(request.getReviewNote().trim());
        assessment.setReviewStatus(TriageReviewStatus.OVERRIDDEN);
        assessment.setReviewedByEmail(reviewerEmail.toLowerCase(Locale.ROOT));
        assessment.setReviewedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));

        SymptomAssessment savedAssessment = symptomAssessmentRepository.save(assessment);
        realtimeNotifier.publishAdminRefresh("triage-overridden");
        realtimeNotifier.publishPatientTriageRefresh(
                savedAssessment.getPatient().getUser().getEmail(),
                "triage-overridden",
                "A clinician reviewed your triage route and updated the care recommendation.",
                "warning");
        metricsService.recordTriageOverride(savedAssessment.getEffectiveDepartment(), savedAssessment.getEffectiveTriageLevel().name());
        auditTrailService.record(AuditEvent.builder()
                .action("TRIAGE_OVERRIDDEN")
                .resourceType("TRIAGE")
                .resourceId(AuditMetadata.id(savedAssessment.getId()))
                .details(AuditMetadata.map(
                        "patientId", savedAssessment.getPatient().getId(),
                        "oldDepartment", savedAssessment.getSuggestedDepartment(),
                        "oldTriageLevel", savedAssessment.getTriageLevel().name(),
                        "newDepartment", savedAssessment.getEffectiveDepartment(),
                        "newTriageLevel", savedAssessment.getEffectiveTriageLevel().name(),
                        "reviewedByEmail", savedAssessment.getReviewedByEmail()))
                .build());
        return savedAssessment;
    }

    private boolean needsReview(TriageRecommendation recommendation) {
        return recommendation.triageLevel() == TriageLevel.RED
                || recommendation.triageLevel() == TriageLevel.ORANGE
                || recommendation.confidenceScore().doubleValue() < 0.70;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s{2,}", " ");
    }

    private String normalizeDepartment(String department) {
        String normalized = department == null ? "" : department.trim().replaceAll("\\s{2,}", " ");
        if (normalized.isBlank()) {
            throw ApiErrorException.badRequest("Department is required");
        }
        return normalized;
    }

    private String toastLevel(TriageLevel triageLevel) {
        return switch (triageLevel) {
            case RED, ORANGE -> "warning";
            case YELLOW -> "info";
            case GREEN -> "success";
        };
    }
}
