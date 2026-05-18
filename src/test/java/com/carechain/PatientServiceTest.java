package com.carechain;

import com.carechain.auth.UserRepository;
import com.carechain.audit.AuditTrailService;
import com.carechain.auth.model.User;
import com.carechain.patient.OpdQueueRepository;
import com.carechain.patient.PatientRepository;
import com.carechain.patient.PatientService;
import com.carechain.patient.model.OpdQueue;
import com.carechain.patient.model.Patient;
import com.carechain.patient.model.QueueStatus;
import com.carechain.realtime.RealtimeNotifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @InjectMocks
    private PatientService patientService;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private OpdQueueRepository opdQueueRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RealtimeNotifier realtimeNotifier;

    @Mock
    private AuditTrailService auditTrailService;

    @Test
    void joinQueue_shouldAssignNextTokenForToday() {
        User user = User.builder().id(10L).email("patient@test.com").build();
        Patient patient = Patient.builder().id(7L).name("Patient").user(user).build();

        when(userRepository.findByEmail("patient@test.com")).thenReturn(Optional.of(user));
        when(patientRepository.findByUserId(10L)).thenReturn(Optional.of(patient));
        when(patientRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(patient));
        when(opdQueueRepository.findByPatientIdAndQueueDateAndStatusInOrderByJoinedAtDesc(
                eq(7L), any(LocalDate.class), any(List.class))).thenReturn(List.of());
        when(opdQueueRepository.findMaxTokenForDepartmentOnDate(eq("Cardiology"), any(LocalDate.class))).thenReturn(4);
        when(opdQueueRepository.saveAndFlush(any(OpdQueue.class))).thenAnswer(invocation -> {
            OpdQueue queue = invocation.getArgument(0);
            queue.setId(21L);
            return queue;
        });

        OpdQueue result = patientService.joinQueue("patient@test.com", " Cardiology ");

        assertEquals(5, result.getTokenNumber());
        assertEquals("Cardiology", result.getDepartment());
        assertEquals(QueueStatus.WAITING, result.getStatus());
        verify(opdQueueRepository).saveAndFlush(any(OpdQueue.class));
    }

    @Test
    void joinQueue_shouldRetryWhenConcurrentTokenCollisionOccurs() {
        User user = User.builder().id(10L).email("patient@test.com").build();
        Patient patient = Patient.builder().id(7L).name("Patient").user(user).build();

        when(userRepository.findByEmail("patient@test.com")).thenReturn(Optional.of(user));
        when(patientRepository.findByUserId(10L)).thenReturn(Optional.of(patient));
        when(patientRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(patient));
        when(opdQueueRepository.findByPatientIdAndQueueDateAndStatusInOrderByJoinedAtDesc(
                eq(7L), any(LocalDate.class), any(List.class))).thenReturn(List.of());
        when(opdQueueRepository.findMaxTokenForDepartmentOnDate(eq("Cardiology"), any(LocalDate.class)))
                .thenReturn(4, 5);
        when(opdQueueRepository.saveAndFlush(any(OpdQueue.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate token"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OpdQueue result = patientService.joinQueue("patient@test.com", "Cardiology");

        assertEquals(6, result.getTokenNumber());
        verify(opdQueueRepository, times(2)).saveAndFlush(any(OpdQueue.class));
    }

    @Test
    void joinQueue_shouldRejectSecondActiveTokenForSameDay() {
        User user = User.builder().id(10L).email("patient@test.com").build();
        Patient patient = Patient.builder().id(7L).name("Patient").user(user).build();
        OpdQueue existing = OpdQueue.builder()
                .id(30L)
                .patient(patient)
                .department("Cardiology")
                .tokenNumber(4)
                .status(QueueStatus.WAITING)
                .queueDate(LocalDate.now())
                .build();

        when(userRepository.findByEmail("patient@test.com")).thenReturn(Optional.of(user));
        when(patientRepository.findByUserId(10L)).thenReturn(Optional.of(patient));
        when(patientRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(patient));
        when(opdQueueRepository.findByPatientIdAndQueueDateAndStatusInOrderByJoinedAtDesc(
                eq(7L), any(LocalDate.class), any(List.class))).thenReturn(List.of(existing));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> patientService.joinQueue("patient@test.com", "Cardiology"));

        assertEquals("You already have an active queue token for today", exception.getMessage());
    }

    @Test
    void findActiveQueueToken_shouldReturnTodayActiveToken() {
        User user = User.builder().id(10L).email("patient@test.com").build();
        Patient patient = Patient.builder().id(7L).name("Patient").user(user).build();
        OpdQueue activeQueue = OpdQueue.builder()
                .id(41L)
                .patient(patient)
                .department("Dermatology")
                .tokenNumber(3)
                .status(QueueStatus.WAITING)
                .queueDate(LocalDate.now())
                .build();

        when(userRepository.findByEmail("patient@test.com")).thenReturn(Optional.of(user));
        when(patientRepository.findByUserId(10L)).thenReturn(Optional.of(patient));
        when(opdQueueRepository.findFirstByPatientIdAndQueueDateAndStatusInOrderByJoinedAtDesc(
                eq(7L), any(LocalDate.class), any(List.class))).thenReturn(Optional.of(activeQueue));

        Optional<OpdQueue> result = patientService.findActiveQueueToken("patient@test.com");

        assertTrue(result.isPresent());
        assertEquals(41L, result.get().getId());
    }

    @Test
    void startQueueEntry_shouldPromoteNextWaitingToken() {
        User user = User.builder().email("patient@test.com").build();
        Patient patient = Patient.builder().user(user).build();
        OpdQueue waitingQueue = OpdQueue.builder()
                .id(50L)
                .patient(patient)
                .department("General Medicine")
                .tokenNumber(2)
                .status(QueueStatus.WAITING)
                .queueDate(LocalDate.now())
                .build();

        when(opdQueueRepository.findById(50L)).thenReturn(Optional.of(waitingQueue));
        when(opdQueueRepository.findFirstByDepartmentAndQueueDateAndStatusOrderByTokenNumberAsc(
                "General Medicine", waitingQueue.getQueueDate(), QueueStatus.IN_PROGRESS)).thenReturn(Optional.empty());
        when(opdQueueRepository.findFirstByDepartmentAndQueueDateAndStatusOrderByTokenNumberAsc(
                "General Medicine", waitingQueue.getQueueDate(), QueueStatus.WAITING)).thenReturn(Optional.of(waitingQueue));
        when(opdQueueRepository.save(any(OpdQueue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OpdQueue result = patientService.startQueueEntry(50L);

        assertEquals(QueueStatus.IN_PROGRESS, result.getStatus());
    }

    @Test
    void startQueueEntry_shouldRejectSkippingAheadInDepartment() {
        User user = User.builder().email("patient@test.com").build();
        Patient patient = Patient.builder().user(user).build();
        OpdQueue selectedQueue = OpdQueue.builder()
                .id(52L)
                .patient(patient)
                .department("Cardiology")
                .tokenNumber(3)
                .status(QueueStatus.WAITING)
                .queueDate(LocalDate.now())
                .build();
        OpdQueue nextWaiting = OpdQueue.builder()
                .id(51L)
                .patient(patient)
                .department("Cardiology")
                .tokenNumber(2)
                .status(QueueStatus.WAITING)
                .queueDate(LocalDate.now())
                .build();

        when(opdQueueRepository.findById(52L)).thenReturn(Optional.of(selectedQueue));
        when(opdQueueRepository.findFirstByDepartmentAndQueueDateAndStatusOrderByTokenNumberAsc(
                "Cardiology", selectedQueue.getQueueDate(), QueueStatus.IN_PROGRESS)).thenReturn(Optional.empty());
        when(opdQueueRepository.findFirstByDepartmentAndQueueDateAndStatusOrderByTokenNumberAsc(
                "Cardiology", selectedQueue.getQueueDate(), QueueStatus.WAITING)).thenReturn(Optional.of(nextWaiting));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> patientService.startQueueEntry(52L));

        assertEquals("Token #2 must be served next in Cardiology", exception.getMessage());
    }

    @Test
    void completeQueueEntry_shouldRequireInProgressStatus() {
        OpdQueue waitingQueue = OpdQueue.builder()
                .id(60L)
                .department("Neurology")
                .tokenNumber(4)
                .status(QueueStatus.WAITING)
                .queueDate(LocalDate.now())
                .build();

        when(opdQueueRepository.findById(60L)).thenReturn(Optional.of(waitingQueue));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> patientService.completeQueueEntry(60L));

        assertEquals("Queue token must be started before it can be completed", exception.getMessage());
    }
}
