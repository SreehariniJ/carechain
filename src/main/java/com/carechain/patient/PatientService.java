package com.carechain.patient;

import com.carechain.audit.AuditEvent;
import com.carechain.audit.AuditMetadata;
import com.carechain.audit.AuditTrailService;
import com.carechain.config.ApiErrorException;
import com.carechain.auth.UserRepository;
import com.carechain.auth.model.User;
import com.carechain.realtime.RealtimeNotifier;
import com.carechain.patient.model.OpdQueue;
import com.carechain.patient.model.Patient;
import com.carechain.patient.model.QueueStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class PatientService {

    private static final List<QueueStatus> ACTIVE_STATUSES = List.of(QueueStatus.WAITING, QueueStatus.IN_PROGRESS);

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private OpdQueueRepository opdQueueRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RealtimeNotifier realtimeNotifier;

    @Autowired
    private AuditTrailService auditTrailService;

    public Patient getPatientByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiErrorException.notFound("User not found"));
        return patientRepository.findByUserId(user.getId())
                .orElseThrow(() -> ApiErrorException.notFound("Patient profile not found"));
    }

    @Transactional
    @PreAuthorize("hasRole('PATIENT')")
    public OpdQueue joinQueue(String email, String department) {
        Patient patient = patientRepository.findByIdForUpdate(getPatientByEmail(email).getId())
                .orElseThrow(() -> ApiErrorException.notFound("Patient profile not found"));
        LocalDate queueDate = LocalDate.now();
        String normalizedDepartment = normalizeDepartment(department);

        // Prevent duplicate queue entries for today
        opdQueueRepository.findByPatientIdAndQueueDateAndStatusInOrderByJoinedAtDesc(
                        patient.getId(), queueDate, ACTIVE_STATUSES).stream()
                .findFirst()
                .ifPresent(q -> {
                    throw ApiErrorException.conflict("You already have an active queue token for today");
                });

        for (int attempt = 0; attempt < 3; attempt++) {
            int maxToken = opdQueueRepository.findMaxTokenForDepartmentOnDate(normalizedDepartment, queueDate);

            OpdQueue queue = OpdQueue.builder()
                    .patient(patient)
                    .department(normalizedDepartment)
                    .tokenNumber(maxToken + 1)
                    .status(QueueStatus.WAITING)
                    .queueDate(queueDate)
                    .build();

            try {
                OpdQueue savedQueue = opdQueueRepository.saveAndFlush(queue);
                realtimeNotifier.publishQueueChange(
                        email,
                        "queue-joined",
                        "Token #" + savedQueue.getTokenNumber() + " joined " + savedQueue.getDepartment() + ".",
                        "info");
                auditTrailService.record(AuditEvent.builder()
                        .action("QUEUE_JOINED")
                        .resourceType("QUEUE")
                        .resourceId(AuditMetadata.id(savedQueue.getId()))
                        .details(AuditMetadata.map(
                                "patientId", patient.getId(),
                                "department", savedQueue.getDepartment(),
                                "queueDate", savedQueue.getQueueDate().toString(),
                                "tokenNumber", savedQueue.getTokenNumber()))
                        .build());
                return savedQueue;
            } catch (DataIntegrityViolationException exception) {
                if (attempt == 2) {
                    throw ApiErrorException.conflict("Unable to assign queue token right now. Please try again.");
                }
            }
        }

        throw ApiErrorException.conflict("Unable to assign queue token right now. Please try again.");
    }

    public OpdQueue getQueueToken(Long queueId) {
        return opdQueueRepository.findById(queueId)
                .orElseThrow(() -> ApiErrorException.notFound("Queue entry not found"));
    }

    public List<OpdQueue> getPatientQueueHistory(String email) {
        Patient patient = getPatientByEmail(email);
        return opdQueueRepository.findByPatientIdOrderByJoinedAtDesc(patient.getId());
    }

    public List<OpdQueue> getActiveQueue() {
        return opdQueueRepository.findActiveQueueWithPatients(
                ACTIVE_STATUSES, LocalDate.now());
    }

    public Optional<OpdQueue> findActiveQueueToken(String email) {
        Patient patient = getPatientByEmail(email);
        return opdQueueRepository.findFirstByPatientIdAndQueueDateAndStatusInOrderByJoinedAtDesc(
                patient.getId(), LocalDate.now(), ACTIVE_STATUSES);
    }

    public long countActiveQueueEntries() {
        return getActiveQueue().size();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public OpdQueue startQueueEntry(Long queueId) {
        OpdQueue queue = getQueueEntry(queueId);

        if (queue.getStatus() == QueueStatus.IN_PROGRESS) {
            throw ApiErrorException.conflict("Queue token is already in progress");
        }
        if (queue.getStatus() == QueueStatus.DONE) {
            throw ApiErrorException.conflict("Completed queue tokens cannot be restarted");
        }

        opdQueueRepository.findFirstByDepartmentAndQueueDateAndStatusOrderByTokenNumberAsc(
                        queue.getDepartment(), queue.getQueueDate(), QueueStatus.IN_PROGRESS)
                .ifPresent(active -> {
                    throw ApiErrorException.conflict("Another patient is already in progress for " + queue.getDepartment());
                });

        OpdQueue nextWaiting = opdQueueRepository.findFirstByDepartmentAndQueueDateAndStatusOrderByTokenNumberAsc(
                        queue.getDepartment(), queue.getQueueDate(), QueueStatus.WAITING)
                .orElseThrow(() -> ApiErrorException.conflict("No waiting queue token found for " + queue.getDepartment()));

        if (!nextWaiting.getId().equals(queue.getId())) {
            throw ApiErrorException.conflict("Token #" + nextWaiting.getTokenNumber() + " must be served next in " + queue.getDepartment());
        }

        queue.setStatus(QueueStatus.IN_PROGRESS);
        OpdQueue savedQueue = opdQueueRepository.save(queue);
        realtimeNotifier.publishQueueChange(
                savedQueue.getPatient().getUser().getEmail(),
                "queue-started",
                "Your turn has started in " + savedQueue.getDepartment() + ".",
                "warning");
        auditTrailService.record(AuditEvent.builder()
                .action("QUEUE_STARTED")
                .resourceType("QUEUE")
                .resourceId(AuditMetadata.id(savedQueue.getId()))
                .details(AuditMetadata.map(
                        "patientId", savedQueue.getPatient().getId(),
                        "department", savedQueue.getDepartment(),
                        "queueDate", savedQueue.getQueueDate().toString(),
                        "tokenNumber", savedQueue.getTokenNumber()))
                .build());
        return savedQueue;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public OpdQueue completeQueueEntry(Long queueId) {
        OpdQueue queue = getQueueEntry(queueId);

        if (queue.getStatus() == QueueStatus.DONE) {
            throw ApiErrorException.conflict("Queue token is already completed");
        }
        if (queue.getStatus() == QueueStatus.WAITING) {
            throw ApiErrorException.conflict("Queue token must be started before it can be completed");
        }

        queue.setStatus(QueueStatus.DONE);
        OpdQueue savedQueue = opdQueueRepository.save(queue);
        realtimeNotifier.publishQueueChange(
                savedQueue.getPatient().getUser().getEmail(),
                "queue-completed",
                "Your " + savedQueue.getDepartment() + " visit has been completed.",
                "success");
        auditTrailService.record(AuditEvent.builder()
                .action("QUEUE_COMPLETED")
                .resourceType("QUEUE")
                .resourceId(AuditMetadata.id(savedQueue.getId()))
                .details(AuditMetadata.map(
                        "patientId", savedQueue.getPatient().getId(),
                        "department", savedQueue.getDepartment(),
                        "queueDate", savedQueue.getQueueDate().toString(),
                        "tokenNumber", savedQueue.getTokenNumber()))
                .build());
        return savedQueue;
    }

    private OpdQueue getQueueEntry(Long queueId) {
        return opdQueueRepository.findById(queueId)
                .orElseThrow(() -> ApiErrorException.notFound("Queue entry not found"));
    }

    private String normalizeDepartment(String department) {
        String normalized = department == null ? "" : department.trim();
        if (normalized.isBlank()) {
            throw ApiErrorException.badRequest("Department is required");
        }
        return normalized.replaceAll("\\s{2,}", " ");
    }
}
