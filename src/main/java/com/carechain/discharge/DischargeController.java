package com.carechain.discharge;

import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/discharges")
public class DischargeController {

    private final DischargeService dischargeService;

    public DischargeController(DischargeService dischargeService) {
        this.dischargeService = dischargeService;
    }

    @GetMapping("/recent")
    public ResponseEntity<List<Map<String, Object>>> getRecentDischarges(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(dischargeService.getRecentDischarges(limit));
    }

    @GetMapping("/{admissionId}")
    public ResponseEntity<Map<String, Object>> getDischargeOverview(@PathVariable Long admissionId) {
        return ResponseEntity.ok(dischargeService.getDischargeOverview(admissionId));
    }

    @PostMapping("/{admissionId}/summary")
    public ResponseEntity<?> saveSummary(@PathVariable Long admissionId,
                                         @Valid @RequestBody DischargeSummaryRequest request,
                                         Authentication authentication) {
        DischargeSummary summary = dischargeService.saveSummary(admissionId, request, authentication.getName());
        return ResponseEntity.ok(Map.of(
                "message", "Discharge summary saved",
                "attendingDoctorName", summary.getAttendingDoctorName(),
                "updatedAt", summary.getUpdatedAt().toString()
        ));
    }

    @GetMapping("/{admissionId}/billing")
    public ResponseEntity<?> getBillingPreview(@PathVariable Long admissionId) {
        BillingPreview preview = dischargeService.getBillingPreview(admissionId);
        return ResponseEntity.ok(Map.of(
                "admissionId", preview.admissionId(),
                "stayDays", preview.stayDays(),
                "currencyCode", preview.currencyCode(),
                "dailyRate", preview.dailyRate(),
                "lineItems", preview.lineItems().stream().map(item -> Map.of(
                        "label", item.label(),
                        "amount", item.amount()
                )).toList(),
                "totalAmount", preview.totalAmount()
        ));
    }

    @GetMapping("/{admissionId}/pdf")
    public ResponseEntity<?> downloadPdf(@PathVariable Long admissionId, Authentication authentication) {
        byte[] pdf = dischargeService.generateDischargePdf(admissionId, authentication.getName());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("carechain-discharge-" + admissionId + ".pdf")
                .build());
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdf);
    }
}
