package com.carechain.appointment;

import com.carechain.audit.AuditEvent;
import com.carechain.audit.AuditMetadata;
import com.carechain.audit.AuditTrailService;
import com.carechain.appointment.model.*;
import com.carechain.config.ApiErrorException;
import com.carechain.auth.UserRepository;
import com.carechain.auth.model.User;
import com.carechain.patient.PatientRepository;
import com.carechain.patient.model.Patient;
import com.carechain.realtime.RealtimeNotifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AppointmentService {

    private static final List<String> ALL_SLOTS = Arrays.asList(
            "09:00", "09:30", "10:00", "10:30", "11:00", "11:30",
            "14:00", "14:30", "15:00", "15:30", "16:00", "16:30"
    );

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RealtimeNotifier realtimeNotifier;

    @Autowired
    private AuditTrailService auditTrailService;

    public List<Doctor> getAllDoctors() {
        return doctorRepository.findAllByOrderByNameAsc();
    }

    public List<String> getAvailableSlots(Long doctorId, LocalDate date) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> ApiErrorException.notFound("Doctor not found"));

        // Check if doctor is available on this day
        String dayOfWeek = date.getDayOfWeek().name().substring(0, 3);
        if (doctor.getAvailableDays() != null
                && !doctor.getAvailableDays().toUpperCase().contains(dayOfWeek)) {
            return List.of();
        }

        // Get already booked slots
        List<Appointment> booked = appointmentRepository
                .findByDoctorIdAndAppointmentDateAndStatusNot(
                        doctorId, date, AppointmentStatus.CANCELLED);

        Set<String> bookedSlots = booked.stream()
                .map(Appointment::getSlot)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        // Return available slots
        return ALL_SLOTS.stream()
                .filter(slot -> !bookedSlots.contains(slot))
                .toList();
    }

    @Transactional
    @PreAuthorize("hasRole('PATIENT')")
    public Appointment bookAppointment(String email, Long doctorId, LocalDate date, String slot) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiErrorException.notFound("User not found"));
        Patient patient = patientRepository.findByUserId(user.getId())
                .orElseThrow(() -> ApiErrorException.notFound("Patient profile not found"));
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> ApiErrorException.notFound("Doctor not found"));
        String normalizedSlot = normalizeSlot(slot);

        if (date == null) {
            throw ApiErrorException.badRequest("Appointment date is required");
        }
        if (date.isBefore(LocalDate.now())) {
            throw ApiErrorException.badRequest("Appointment date cannot be in the past");
        }
        if (!ALL_SLOTS.contains(normalizedSlot)) {
            throw ApiErrorException.badRequest("Slot must be one of the configured appointment times");
        }

        // Validate slot availability
        List<String> available = getAvailableSlots(doctorId, date);
        if (!available.contains(normalizedSlot)) {
            throw ApiErrorException.conflict("Slot " + normalizedSlot + " is not available");
        }

        Appointment appointment = Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .appointmentDate(date)
                .slot(normalizedSlot)
                .status(AppointmentStatus.BOOKED)
                .build();

        try {
            Appointment savedAppointment = appointmentRepository.saveAndFlush(appointment);
            realtimeNotifier.publishAppointmentChange(
                    email,
                    doctor.getUser().getEmail(),
                    "appointment-booked",
                    "Appointment confirmed with " + doctor.getName() + " on " + date + " at " + normalizedSlot + ".",
                    "success");
            auditTrailService.record(AuditEvent.builder()
                    .action("APPOINTMENT_BOOKED")
                    .resourceType("APPOINTMENT")
                    .resourceId(AuditMetadata.id(savedAppointment.getId()))
                    .details(AuditMetadata.map(
                            "patientId", patient.getId(),
                            "doctorId", doctor.getId(),
                            "appointmentDate", savedAppointment.getAppointmentDate().toString(),
                            "slot", savedAppointment.getSlot()))
                    .build());
            return savedAppointment;
        } catch (DataIntegrityViolationException exception) {
            throw ApiErrorException.conflict("Slot " + normalizedSlot + " is not available");
        }
    }

    @Transactional
    public Appointment cancelAppointment(Long appointmentId, String email) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> ApiErrorException.notFound("Appointment not found"));

        if (!appointment.getPatient().getUser().getEmail().equals(email)) {
            throw ApiErrorException.forbidden("Not authorized to cancel this appointment");
        }

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw ApiErrorException.conflict("Appointment is already cancelled");
        }
        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw ApiErrorException.conflict("Completed appointments cannot be cancelled");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        Appointment savedAppointment = appointmentRepository.save(appointment);
        realtimeNotifier.publishAppointmentChange(
                email,
                appointment.getDoctor().getUser().getEmail(),
                "appointment-cancelled",
                "Your appointment with " + appointment.getDoctor().getName() + " was cancelled.",
                "warning");
        auditTrailService.record(AuditEvent.builder()
                .action("APPOINTMENT_CANCELLED")
                .resourceType("APPOINTMENT")
                .resourceId(AuditMetadata.id(savedAppointment.getId()))
                .details(AuditMetadata.map(
                        "patientId", savedAppointment.getPatient().getId(),
                        "doctorId", savedAppointment.getDoctor().getId(),
                        "appointmentDate", savedAppointment.getAppointmentDate() != null ? savedAppointment.getAppointmentDate().toString() : null,
                        "slot", savedAppointment.getSlot()))
                .build());
        return savedAppointment;
    }

    @Transactional
    public Appointment completeAppointment(Long appointmentId, String email) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> ApiErrorException.notFound("Appointment not found"));

        if (!appointment.getDoctor().getUser().getEmail().equals(email)) {
            throw ApiErrorException.forbidden("Not authorized to complete this appointment");
        }
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw ApiErrorException.conflict("Cancelled appointments cannot be completed");
        }
        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw ApiErrorException.conflict("Appointment is already completed");
        }

        appointment.setStatus(AppointmentStatus.COMPLETED);
        Appointment savedAppointment = appointmentRepository.save(appointment);
        realtimeNotifier.publishAppointmentChange(
                appointment.getPatient().getUser().getEmail(),
                email,
                "appointment-completed",
                appointment.getDoctor().getName() + " completed your appointment at " + appointment.getSlot() + ".",
                "success");
        auditTrailService.record(AuditEvent.builder()
                .action("APPOINTMENT_COMPLETED")
                .resourceType("APPOINTMENT")
                .resourceId(AuditMetadata.id(savedAppointment.getId()))
                .details(AuditMetadata.map(
                        "patientId", savedAppointment.getPatient().getId(),
                        "doctorId", savedAppointment.getDoctor().getId(),
                        "appointmentDate", savedAppointment.getAppointmentDate() != null ? savedAppointment.getAppointmentDate().toString() : null,
                        "slot", savedAppointment.getSlot()))
                .build());
        return savedAppointment;
    }

    public List<Appointment> getPatientAppointments(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiErrorException.notFound("User not found"));
        Patient patient = patientRepository.findByUserId(user.getId())
                .orElseThrow(() -> ApiErrorException.notFound("Patient profile not found"));
        return appointmentRepository.findPatientAppointmentsWithDoctor(patient.getId());
    }

    public List<Appointment> getDoctorSchedule(String email, LocalDate date) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiErrorException.notFound("User not found"));
        Doctor doctor = doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> ApiErrorException.notFound("Doctor profile not found"));
        return appointmentRepository.findTodaySchedule(doctor.getId(), date);
    }

    public long countTodayAppointments() {
        return appointmentRepository.countByAppointmentDate(LocalDate.now());
    }

    private String normalizeSlot(String slot) {
        return slot == null ? "" : slot.trim().toUpperCase(Locale.ROOT);
    }
}
