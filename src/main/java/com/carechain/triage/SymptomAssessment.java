package com.carechain.triage;

import com.carechain.patient.model.Patient;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "symptom_assessments", indexes = {
        @Index(name = "idx_symptom_assessments_patient_submitted", columnList = "patient_id, submitted_at"),
        @Index(name = "idx_symptom_assessments_review_status", columnList = "review_status, submitted_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SymptomAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "symptom_text", nullable = false, length = 2000)
    private String symptomText;

    @Column(name = "suggested_department", nullable = false, length = 80)
    private String suggestedDepartment;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "triage_level", nullable = false, length = 20)
    private TriageLevel triageLevel;

    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "routing_rationale", length = 500)
    private String routingRationale;

    @Column(name = "matched_signals", length = 500)
    private String matchedSignals;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "review_status", nullable = false, length = 20)
    private TriageReviewStatus reviewStatus;

    @Column(name = "reviewed_department", length = 80)
    private String reviewedDepartment;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reviewed_triage_level", length = 20)
    private TriageLevel reviewedTriageLevel;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "reviewed_by_email", length = 120)
    private String reviewedByEmail;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @PrePersist
    protected void onCreate() {
        if (submittedAt == null) {
            submittedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        }
    }

    public String getEffectiveDepartment() {
        return reviewedDepartment != null && !reviewedDepartment.isBlank() ? reviewedDepartment : suggestedDepartment;
    }

    public TriageLevel getEffectiveTriageLevel() {
        return reviewedTriageLevel != null ? reviewedTriageLevel : triageLevel;
    }
}
