package com.carechain.patient;

import com.carechain.patient.model.OpdQueue;
import com.carechain.patient.model.Patient;
import com.carechain.patient.model.QueueStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class PatientController {

    @Autowired
    private PatientService patientService;

    @GetMapping("/patients/me")
    public ResponseEntity<?> getMyProfile(Authentication auth) {
        Patient patient = patientService.getPatientByEmail(auth.getName());
        return ResponseEntity.ok(Map.of(
                "id", patient.getId(),
                "name", patient.getName() != null ? patient.getName() : "",
                "age", patient.getAge() != null ? patient.getAge() : 0,
                "bloodGroup", patient.getBloodGroup() != null ? patient.getBloodGroup() : "",
                "phone", patient.getPhone() != null ? patient.getPhone() : ""
        ));
    }

    @PostMapping("/queue/join/{department}")
    public ResponseEntity<?> joinQueue(@PathVariable String department, Authentication auth) {
        OpdQueue queue = patientService.joinQueue(auth.getName(), department);
        return ResponseEntity.ok(Map.of(
                "id", queue.getId(),
                "department", queue.getDepartment(),
                "tokenNumber", queue.getTokenNumber(),
                "status", queue.getStatus().name()
        ));
    }

    @GetMapping("/queue/token/{id}")
    public ResponseEntity<?> getQueueToken(@PathVariable Long id) {
        OpdQueue queue = patientService.getQueueToken(id);
        return ResponseEntity.ok(Map.of(
                "id", queue.getId(),
                "department", queue.getDepartment(),
                "tokenNumber", queue.getTokenNumber(),
                "status", queue.getStatus().name(),
                "joinedAt", queue.getJoinedAt().toString()
        ));
    }

    @GetMapping("/queue/me/active")
    public ResponseEntity<?> getMyActiveQueue(Authentication auth) {
        Optional<OpdQueue> activeQueue = patientService.findActiveQueueToken(auth.getName());
        if (activeQueue.isEmpty()) {
            return ResponseEntity.ok(Map.of("active", false));
        }

        OpdQueue queue = activeQueue.get();
        List<OpdQueue> departmentQueue = patientService.getActiveQueue().stream()
                .filter(entry -> entry.getQueueDate().equals(queue.getQueueDate()))
                .filter(entry -> entry.getDepartment().equals(queue.getDepartment()))
                .toList();

        long peopleAhead = departmentQueue.stream()
                .filter(entry -> entry.getTokenNumber() < queue.getTokenNumber())
                .count();

        Integer servingToken = departmentQueue.stream()
                .filter(entry -> entry.getStatus() == QueueStatus.IN_PROGRESS)
                .map(OpdQueue::getTokenNumber)
                .findFirst()
                .orElse(null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("active", true);
        response.put("id", queue.getId());
        response.put("department", queue.getDepartment());
        response.put("tokenNumber", queue.getTokenNumber());
        response.put("status", queue.getStatus().name());
        response.put("joinedAt", queue.getJoinedAt().toString());
        response.put("peopleAhead", peopleAhead);
        response.put("currentlyServing", servingToken);
        return ResponseEntity.ok(response);
    }
}
