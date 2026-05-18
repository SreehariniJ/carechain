package com.carechain.triage;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/triage")
public class TriageController {

    private final TriageService triageService;

    public TriageController(TriageService triageService) {
        this.triageService = triageService;
    }

    @PostMapping("/assess")
    public ResponseEntity<?> submitAssessment(@Valid @RequestBody SymptomAssessmentRequest request,
                                              Authentication authentication) {
        SymptomAssessment assessment = triageService.submitAssessment(authentication.getName(), request);
        return ResponseEntity.ok(TriageResponseMapper.toPatientResponse(assessment));
    }

    @GetMapping("/me")
    public ResponseEntity<List<Map<String, Object>>> getMyAssessments(Authentication authentication) {
        List<Map<String, Object>> payload = triageService.getPatientAssessments(authentication.getName()).stream()
                .map(TriageResponseMapper::toPatientResponse)
                .toList();
        return ResponseEntity.ok(payload);
    }
}
