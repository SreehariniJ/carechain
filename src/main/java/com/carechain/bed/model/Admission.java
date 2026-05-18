package com.carechain.bed.model;

import com.carechain.patient.model.Patient;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "admissions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_admissions_patient_active", columnNames = {"patient_id", "active_record_key"}),
                @UniqueConstraint(name = "uk_admissions_bed_active", columnNames = {"bed_id", "active_record_key"})
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Admission {

    public static final String ACTIVE_RECORD_KEY = "ACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bed_id", nullable = false)
    private Bed bed;

    @Column(name = "admitted_at")
    private LocalDateTime admittedAt;

    @Column(name = "discharged_at")
    private LocalDateTime dischargedAt;

    @Column(name = "active_record_key", length = 20)
    private String activeRecordKey;

    @PrePersist
    protected void onCreate() {
        if (this.admittedAt == null) {
            this.admittedAt = LocalDateTime.now();
        }
        if (this.activeRecordKey == null) {
            this.activeRecordKey = ACTIVE_RECORD_KEY;
        }
    }
}
