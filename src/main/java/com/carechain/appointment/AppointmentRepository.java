package com.carechain.appointment;

import com.carechain.appointment.model.Appointment;
import com.carechain.appointment.model.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByDoctorIdAndAppointmentDateAndStatusNot(
            Long doctorId, LocalDate date, AppointmentStatus status);

    List<Appointment> findByDoctorIdAndAppointmentDate(Long doctorId, LocalDate date);

    @Query("SELECT a FROM Appointment a JOIN FETCH a.doctor WHERE a.patient.id = :patientId ORDER BY a.appointmentDate DESC")
    List<Appointment> findPatientAppointmentsWithDoctor(@Param("patientId") Long patientId);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.appointmentDate = :date AND a.status = :status")
    long countByDateAndStatus(@Param("date") LocalDate date, @Param("status") AppointmentStatus status);

    @Query("SELECT a FROM Appointment a JOIN FETCH a.patient JOIN FETCH a.doctor WHERE a.doctor.id = :doctorId AND a.appointmentDate = :date AND a.status <> 'CANCELLED' ORDER BY a.slot")
    List<Appointment> findTodaySchedule(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);

    long countByAppointmentDate(LocalDate date);
}
