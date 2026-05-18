package com.carechain.patient.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "opd_queue",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_opd_queue_department_date_token",
                columnNames = {"department", "queue_date", "token_number"}
        )
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OpdQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(length = 50, nullable = false)
    private String department;

    @Column(name = "token_number", nullable = false)
    private Integer tokenNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private QueueStatus status;

    @Column(name = "joined_at", updatable = false)
    private LocalDateTime joinedAt;

    @Column(name = "queue_date", nullable = false, updatable = false)
    private LocalDate queueDate;

    @PrePersist
    protected void onCreate() {
        if (this.joinedAt == null) {
            this.joinedAt = LocalDateTime.now();
        }
        if (this.queueDate == null) {
            this.queueDate = LocalDate.now();
        }
    }
}
