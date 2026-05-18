package com.carechain.discharge;

import com.carechain.bed.model.Admission;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "discharge_summaries", uniqueConstraints = {
        @UniqueConstraint(name = "uk_discharge_summaries_admission", columnNames = "admission_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DischargeSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admission_id", nullable = false)
    private Admission admission;

    @Column(nullable = false, length = 2000)
    private String diagnosis;

    @Column(name = "treatment_summary", length = 2000)
    private String treatmentSummary;

    @Column(name = "discharge_instructions", length = 2000)
    private String dischargeInstructions;

    @Column(length = 2000)
    private String medications;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "attending_doctor_name", length = 100)
    private String attendingDoctorName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
