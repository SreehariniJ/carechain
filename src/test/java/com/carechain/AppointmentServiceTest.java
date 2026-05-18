package com.carechain;

import com.carechain.appointment.AppointmentRepository;
import com.carechain.appointment.AppointmentService;
import com.carechain.appointment.DoctorRepository;
import com.carechain.audit.AuditTrailService;
import com.carechain.appointment.model.*;
import com.carechain.auth.UserRepository;
import com.carechain.auth.model.User;
import com.carechain.auth.model.Role;
import com.carechain.patient.PatientRepository;
import com.carechain.patient.model.Patient;
import com.carechain.realtime.RealtimeNotifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @InjectMocks
    private AppointmentService appointmentService;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RealtimeNotifier realtimeNotifier;

    @Mock
    private AuditTrailService auditTrailService;

    @Test
    void getAvailableSlots_shouldExcludeBookedSlots() {
        Doctor doctor = Doctor.builder().id(1L).name("Dr. Smith").availableDays("MON,TUE,WED,THU,FRI").build();
        LocalDate date = LocalDate.of(2026, 5, 18); // Monday

        Appointment booked = Appointment.builder().slot("09:00").build();

        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findByDoctorIdAndAppointmentDateAndStatusNot(
                1L, date, AppointmentStatus.CANCELLED)).thenReturn(List.of(booked));

        List<String> slots = appointmentService.getAvailableSlots(1L, date);

        assertFalse(slots.contains("09:00"));
        assertTrue(slots.contains("09:30"));
        assertTrue(slots.contains("10:00"));
        assertEquals(11, slots.size()); // 12 total - 1 booked
    }

    @Test
    void getAvailableSlots_shouldReturnEmptyWhenDoctorUnavailable() {
        Doctor doctor = Doctor.builder().id(1L).name("Dr. Smith").availableDays("MON,TUE").build();
        LocalDate saturday = LocalDate.of(2026, 5, 23); // Saturday

        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));

        List<String> slots = appointmentService.getAvailableSlots(1L, saturday);

        assertTrue(slots.isEmpty());
    }

    @Test
    void bookAppointment_shouldCreateAppointment() {
        User user = User.builder().id(1L).email("patient@test.com").role(Role.PATIENT).build();
        Patient patient = Patient.builder().id(1L).user(user).name("John").build();
        User doctorUser = User.builder().id(2L).email("doctor@test.com").role(Role.DOCTOR).build();
        Doctor doctor = Doctor.builder().id(1L).user(doctorUser).name("Dr. Smith").availableDays("MON,TUE,WED,THU,FRI").build();
        LocalDate date = LocalDate.of(2026, 5, 18);

        when(userRepository.findByEmail("patient@test.com")).thenReturn(Optional.of(user));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findByDoctorIdAndAppointmentDateAndStatusNot(
                1L, date, AppointmentStatus.CANCELLED)).thenReturn(List.of());
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });

        Appointment result = appointmentService.bookAppointment("patient@test.com", 1L, date, "10:00");

        assertNotNull(result);
        assertEquals(AppointmentStatus.BOOKED, result.getStatus());
        assertEquals("10:00", result.getSlot());
        verify(appointmentRepository).saveAndFlush(any(Appointment.class));
    }

    @Test
    void bookAppointment_shouldThrowWhenSlotTaken() {
        User user = User.builder().id(1L).email("patient@test.com").role(Role.PATIENT).build();
        Patient patient = Patient.builder().id(1L).user(user).name("John").build();
        Doctor doctor = Doctor.builder().id(1L).name("Dr. Smith").availableDays("MON,TUE,WED,THU,FRI").build();
        LocalDate date = LocalDate.of(2026, 5, 18);

        Appointment existing = Appointment.builder().slot("10:00").build();

        when(userRepository.findByEmail("patient@test.com")).thenReturn(Optional.of(user));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findByDoctorIdAndAppointmentDateAndStatusNot(
                1L, date, AppointmentStatus.CANCELLED)).thenReturn(List.of(existing));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> appointmentService.bookAppointment("patient@test.com", 1L, date, "10:00"));
        assertTrue(ex.getMessage().contains("not available"));
    }

    @Test
    void bookAppointment_shouldRejectPastDates() {
        User user = User.builder().id(1L).email("patient@test.com").role(Role.PATIENT).build();
        Patient patient = Patient.builder().id(1L).user(user).name("John").build();
        Doctor doctor = Doctor.builder().id(1L).name("Dr. Smith").availableDays("MON,TUE,WED,THU,FRI").build();

        when(userRepository.findByEmail("patient@test.com")).thenReturn(Optional.of(user));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> appointmentService.bookAppointment("patient@test.com", 1L, LocalDate.now().minusDays(1), "10:00"));

        assertEquals("Appointment date cannot be in the past", ex.getMessage());
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void bookAppointment_shouldRejectUnknownSlots() {
        User user = User.builder().id(1L).email("patient@test.com").role(Role.PATIENT).build();
        Patient patient = Patient.builder().id(1L).user(user).name("John").build();
        Doctor doctor = Doctor.builder().id(1L).name("Dr. Smith").availableDays("MON,TUE,WED,THU,FRI").build();
        LocalDate date = LocalDate.now().plusDays(1);

        when(userRepository.findByEmail("patient@test.com")).thenReturn(Optional.of(user));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> appointmentService.bookAppointment("patient@test.com", 1L, date, "13:15"));

        assertEquals("Slot must be one of the configured appointment times", ex.getMessage());
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void cancelAppointment_shouldUpdateStatus() {
        User user = User.builder().email("patient@test.com").build();
        Patient patient = Patient.builder().user(user).build();
        User doctorUser = User.builder().email("doctor@test.com").build();
        Doctor doctor = Doctor.builder().user(doctorUser).name("Dr. Smith").build();
        Appointment appt = Appointment.builder()
                .id(1L)
                .status(AppointmentStatus.BOOKED)
                .patient(patient)
                .doctor(doctor)
                .appointmentDate(LocalDate.now().plusDays(1))
                .slot("10:00")
                .build();
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Appointment result = appointmentService.cancelAppointment(1L, "patient@test.com");

        assertEquals(AppointmentStatus.CANCELLED, result.getStatus());
    }

    @Test
    void cancelAppointment_shouldThrowWhenAlreadyCancelled() {
        User user = User.builder().email("patient@test.com").build();
        Patient patient = Patient.builder().user(user).build();
        Appointment appt = Appointment.builder().id(1L).status(AppointmentStatus.CANCELLED).patient(patient).build();
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

        assertThrows(RuntimeException.class, () -> appointmentService.cancelAppointment(1L, "patient@test.com"));
    }

    @Test
    void cancelAppointment_shouldThrowWhenCompleted() {
        User user = User.builder().email("patient@test.com").build();
        Patient patient = Patient.builder().user(user).build();
        Appointment appt = Appointment.builder().id(1L).status(AppointmentStatus.COMPLETED).patient(patient).build();
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> appointmentService.cancelAppointment(1L, "patient@test.com"));

        assertEquals("Completed appointments cannot be cancelled", ex.getMessage());
    }

    @Test
    void completeAppointment_shouldThrowWhenCancelled() {
        User doctorUser = User.builder().email("doctor@test.com").build();
        Doctor doctor = Doctor.builder().user(doctorUser).build();
        Appointment appt = Appointment.builder()
                .id(1L)
                .status(AppointmentStatus.CANCELLED)
                .doctor(doctor)
                .appointmentDate(LocalDate.now())
                .slot("11:00")
                .build();
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> appointmentService.completeAppointment(1L, "doctor@test.com"));

        assertEquals("Cancelled appointments cannot be completed", ex.getMessage());
    }
}
