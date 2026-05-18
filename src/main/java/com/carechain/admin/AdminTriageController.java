package com.carechain.admin;

import com.carechain.triage.SymptomAssessment;
import com.carechain.triage.TriageModelReport;
import com.carechain.triage.TriageModelRetrainRequest;
import com.carechain.triage.TriageOverrideRequest;
import com.carechain.triage.TriageResponseMapper;
import com.carechain.triage.TriageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/triage")
public class AdminTriageController {

    private final TriageService triageService;

    public AdminTriageController(TriageService triageService) {
        this.triageService = triageService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getRecentAssessments(@RequestParam(defaultValue = "12") int limit) {
        List<Map<String, Object>> payload = triageService.getRecentAssessments(limit).stream()
                .map(TriageResponseMapper::toAdminResponse)
                .toList();
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/model")
    public ResponseEntity<TriageModelReport> getModelReport() {
        return ResponseEntity.ok(triageService.getModelReport());
    }

    @PostMapping("/model/retrain")
    public ResponseEntity<TriageModelReport> retrainModel(@Valid @RequestBody(required = false) TriageModelRetrainRequest request,
                                                          Authentication authentication) {
        return ResponseEntity.ok(triageService.retrainModel(request, authentication.getName()));
    }

    @PutMapping("/{assessmentId}/override")
    public ResponseEntity<?> overrideAssessment(@PathVariable Long assessmentId,
                                                @Valid @RequestBody TriageOverrideRequest request,
                                                Authentication authentication) {
        SymptomAssessment assessment = triageService.overrideAssessment(assessmentId, request, authentication.getName());
        return ResponseEntity.ok(TriageResponseMapper.toAdminResponse(assessment));
    }
}
