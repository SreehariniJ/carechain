package com.carechain.admin;

import com.carechain.audit.AuditOverview;
import com.carechain.audit.AuditTrailService;
import com.carechain.appointment.AppointmentService;
import com.carechain.bed.BedService;
import com.carechain.bed.model.BedStatus;
import com.carechain.config.ApiErrorException;
import com.carechain.patient.PatientRepository;
import com.carechain.patient.PatientService;
import com.carechain.patient.model.OpdQueue;
import com.carechain.patient.model.QueueStatus;
import com.carechain.appointment.DoctorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@Validated
public class AdminController {

    @Autowired
    private BedService bedService;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private PatientService patientService;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private com.carechain.bed.AdmissionRepository admissionRepository;

    @Autowired
    private AdminProvisioningService adminProvisioningService;

    @Autowired
    private AuditTrailService auditTrailService;

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardStats() {
        DashboardStats stats = DashboardStats.builder()
                .totalBeds(bedService.countTotal())
                .occupiedBeds(bedService.countByStatus(BedStatus.OCCUPIED))
                .availableBeds(bedService.countByStatus(BedStatus.AVAILABLE))
                .maintenanceBeds(bedService.countByStatus(BedStatus.MAINTENANCE))
                .todayAppointments(appointmentService.countTodayAppointments())
                .waitingInQueue(patientService.countActiveQueueEntries())
                .totalPatients(patientRepository.count())
                .totalDoctors(doctorRepository.count())
                .build();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/beds")
    public ResponseEntity<?> getAllBeds() {
        Set<Long> dischargeableBedIds = admissionRepository.findActiveAdmissions().stream()
                .map(admission -> admission.getBed().getId())
                .collect(Collectors.toSet());

        return ResponseEntity.ok(bedService.getAllBedsWithWard().stream().map(b -> Map.of(
                "id", b.getId(),
                "bedNumber", b.getBedNumber(),
                "status", b.getStatus().name(),
                "wardName", b.getWard().getName(),
                "wardType", b.getWard().getType().name(),
                "canDischarge", dischargeableBedIds.contains(b.getId())
        )).collect(Collectors.toList()));
    }

    @GetMapping("/queue")
    public ResponseEntity<?> getActiveQueue() {
        List<OpdQueue> queue = patientService.getActiveQueue();
        Map<String, Boolean> departmentHasInProgress = new HashMap<>();
        Set<String> firstWaitingAssigned = new HashSet<>();
        Set<Long> startableQueueIds = new HashSet<>();

        queue.stream()
                .filter(entry -> entry.getStatus() == QueueStatus.IN_PROGRESS)
                .forEach(entry -> departmentHasInProgress.put(entry.getDepartment(), true));

        for (OpdQueue entry : queue) {
            if (entry.getStatus() == QueueStatus.WAITING
                    && !departmentHasInProgress.getOrDefault(entry.getDepartment(), false)
                    && firstWaitingAssigned.add(entry.getDepartment())) {
                startableQueueIds.add(entry.getId());
            }
        }

        List<Map<String, Object>> result = queue.stream().map(q -> Map.<String, Object>of(
                "id", q.getId(),
                "patientName", q.getPatient().getName() != null ? q.getPatient().getName() : "N/A",
                "department", q.getDepartment(),
                "tokenNumber", q.getTokenNumber(),
                "status", q.getStatus().name(),
                "canStart", startableQueueIds.contains(q.getId()),
                "canComplete", q.getStatus() == QueueStatus.IN_PROGRESS
        )).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/patients")
    public ResponseEntity<?> getAllPatients() {
        return ResponseEntity.ok(patientRepository.findAll().stream().map(p -> Map.of(
                "id", p.getId(),
                "name", p.getName() != null ? p.getName() : "N/A"
        )).collect(Collectors.toList()));
    }

    @PostMapping("/admit/{patientId}")
    public ResponseEntity<?> admitPatient(@PathVariable Long patientId) {
        bedService.admitPatient(patientId);
        return ResponseEntity.ok(Map.of("message", "Patient admitted successfully"));
    }

    @PostMapping("/discharge/{bedId}")
    public ResponseEntity<?> dischargePatient(@PathVariable Long bedId) {
        com.carechain.bed.model.Admission admission = admissionRepository.findByBedIdAndActiveRecordKey(
                        bedId, com.carechain.bed.model.Admission.ACTIVE_RECORD_KEY)
                .orElseThrow(() -> ApiErrorException.notFound("No active admission for this bed"));
        bedService.dischargePatient(admission.getId());
        return ResponseEntity.ok(Map.of("message", "Patient discharged successfully"));
    }

    @PostMapping("/queue/{queueId}/start")
    public ResponseEntity<?> startQueueEntry(@PathVariable Long queueId) {
        OpdQueue queue = patientService.startQueueEntry(queueId);
        return ResponseEntity.ok(Map.of(
                "id", queue.getId(),
                "department", queue.getDepartment(),
                "tokenNumber", queue.getTokenNumber(),
                "status", queue.getStatus().name(),
                "message", "Queue token started"
        ));
    }

    @PostMapping("/queue/{queueId}/complete")
    public ResponseEntity<?> completeQueueEntry(@PathVariable Long queueId) {
        OpdQueue queue = patientService.completeQueueEntry(queueId);
        return ResponseEntity.ok(Map.of(
                "id", queue.getId(),
                "department", queue.getDepartment(),
                "tokenNumber", queue.getTokenNumber(),
                "status", queue.getStatus().name(),
                "message", "Queue token completed"
        ));
    }

    @PostMapping("/doctors")
    public ResponseEntity<?> createDoctor(@Valid @RequestBody CreateDoctorRequest request) {
        com.carechain.appointment.model.Doctor doctor = adminProvisioningService.createDoctor(request);
        return ResponseEntity.ok(Map.of(
                "id", doctor.getId(),
                "email", doctor.getUser().getEmail(),
                "name", doctor.getName(),
                "specialization", doctor.getSpecialization(),
                "availableDays", doctor.getAvailableDays()
        ));
    }

    @PostMapping("/wards")
    public ResponseEntity<?> createWard(@Valid @RequestBody CreateWardRequest request) {
        com.carechain.bed.model.Ward ward = adminProvisioningService.createWard(request);
        return ResponseEntity.ok(Map.of(
                "id", ward.getId(),
                "name", ward.getName(),
                "type", ward.getType().name(),
                "totalBeds", ward.getTotalBeds(),
                "availableBeds", ward.getAvailableBeds()
        ));
    }

    @GetMapping("/audit")
    public ResponseEntity<?> getAuditTrail(@RequestParam(defaultValue = "20") int limit) {
        AuditOverview overview = auditTrailService.getOverview(limit);
        Map<String, Object> integrity = new HashMap<>();
        integrity.put("verified", overview.integrity().verified());
        integrity.put("checkedEntries", overview.integrity().checkedEntries());
        integrity.put("mismatchEntryId", overview.integrity().mismatchEntryId());
        integrity.put("mismatchReason", overview.integrity().mismatchReason());
        integrity.put("headHash", overview.integrity().headHash());

        List<Map<String, Object>> entries = overview.entries().stream().map(entry -> {
            Map<String, Object> payload = new HashMap<>();
            payload.put("id", entry.getId());
            payload.put("occurredAt", entry.getOccurredAt().toString());
            payload.put("actorEmail", entry.getActorEmail());
            payload.put("actorRole", entry.getActorRole());
            payload.put("action", entry.getAction());
            payload.put("resourceType", entry.getResourceType());
            payload.put("resourceId", entry.getResourceId() == null ? "" : entry.getResourceId());
            payload.put("outcome", entry.getOutcome().name());
            payload.put("requestMethod", entry.getRequestMethod() == null ? "" : entry.getRequestMethod());
            payload.put("requestPath", entry.getRequestPath() == null ? "" : entry.getRequestPath());
            payload.put("ipAddress", entry.getIpAddress() == null ? "" : entry.getIpAddress());
            payload.put("detailsJson", entry.getDetailsJson());
            payload.put("previousHash", entry.getPreviousHash() == null ? "" : entry.getPreviousHash());
            payload.put("entryHash", entry.getEntryHash());
            return payload;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "integrity", integrity,
                "entries", entries
        ));
    }
}
