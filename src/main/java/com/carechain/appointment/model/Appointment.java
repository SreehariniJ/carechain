package com.carechain.appointment.model;

import com.carechain.patient.model.Patient;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

@Entity
@Table(
        name = "appointments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_appointments_doctor_date_slot",
                columnNames = {"doctor_id", "appointment_date", "slot"}
        )
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(length = 20, nullable = false)
    private String slot;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status;
}
