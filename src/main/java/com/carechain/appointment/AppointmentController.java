package com.carechain.appointment;

import com.carechain.appointment.model.Appointment;
import com.carechain.appointment.model.AppointmentBookingRequest;
import com.carechain.appointment.model.Doctor;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class AppointmentController {

    @Autowired
    private AppointmentService appointmentService;

    @GetMapping("/doctors")
    public ResponseEntity<?> getAllDoctors() {
        List<Doctor> doctors = appointmentService.getAllDoctors();
        List<Map<String, Object>> result = doctors.stream().map(d -> Map.<String, Object>of(
                "id", d.getId(),
                "name", d.getName() != null ? d.getName() : "",
                "specialization", d.getSpecialization() != null ? d.getSpecialization() : "",
                "availableDays", d.getAvailableDays() != null ? d.getAvailableDays() : ""
        )).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/appointments/slots/{doctorId}/{date}")
    public ResponseEntity<?> getAvailableSlots(
            @PathVariable Long doctorId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<String> slots = appointmentService.getAvailableSlots(doctorId, date);
        return ResponseEntity.ok(Map.of("slots", slots));
    }

    @PostMapping("/appointments/book")
    public ResponseEntity<?> bookAppointment(@Valid @RequestBody AppointmentBookingRequest request,
                                               Authentication auth) {
        Appointment appt = appointmentService.bookAppointment(
                auth.getName(), request.getDoctorId(), request.getDate(), request.getSlot());

        return ResponseEntity.ok(Map.of(
                "id", appt.getId(),
                "doctorName", appt.getDoctor().getName(),
                "date", appt.getAppointmentDate().toString(),
                "slot", appt.getSlot(),
                "status", appt.getStatus().name()
        ));
    }

    @DeleteMapping("/appointments/cancel/{id}")
    public ResponseEntity<?> cancelAppointment(@PathVariable Long id, Authentication auth) {
        appointmentService.cancelAppointment(id, auth.getName());
        return ResponseEntity.ok(Map.of("message", "Appointment cancelled successfully"));
    }

    @GetMapping("/appointments/me")
    public ResponseEntity<?> getPatientAppointments(Authentication auth) {
        List<Appointment> apps = appointmentService.getPatientAppointments(auth.getName());
        return ResponseEntity.ok(apps.stream().map(a -> Map.of(
                "id", a.getId(),
                "doctorName", a.getDoctor().getName(),
                "date", a.getAppointmentDate().toString(),
                "slot", a.getSlot(),
                "status", a.getStatus().name()
        )).collect(Collectors.toList()));
    }

    @GetMapping("/appointments/schedule")
    public ResponseEntity<?> getDoctorSchedule(Authentication auth) {
        List<Appointment> apps = appointmentService.getDoctorSchedule(auth.getName(), LocalDate.now());
        return ResponseEntity.ok(apps.stream().map(a -> Map.of(
                "id", a.getId(),
                "patientName", a.getPatient().getName(),
                "date", a.getAppointmentDate().toString(),
                "slot", a.getSlot(),
                "status", a.getStatus().name()
        )).collect(Collectors.toList()));
    }

    @PutMapping("/appointments/{id}/complete")
    public ResponseEntity<?> completeAppointment(@PathVariable Long id, Authentication auth) {
        appointmentService.completeAppointment(id, auth.getName());
        return ResponseEntity.ok(Map.of("message", "Appointment marked as completed"));
    }
}
