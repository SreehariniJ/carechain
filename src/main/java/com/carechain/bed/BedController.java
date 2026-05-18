package com.carechain.bed;

import com.carechain.bed.model.Admission;
import com.carechain.bed.model.Bed;
import com.carechain.bed.model.Ward;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/beds")
public class BedController {

    @Autowired
    private BedService bedService;

    @GetMapping("/availability")
    public ResponseEntity<?> getAvailability() {
        List<Ward> wards = bedService.getWardAvailability();
        List<Map<String, Object>> result = wards.stream().map(w -> Map.<String, Object>of(
                "id", w.getId(),
                "name", w.getName(),
                "type", w.getType().name(),
                "totalBeds", w.getTotalBeds(),
                "availableBeds", w.getAvailableBeds()
        )).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllBeds() {
        List<Bed> beds = bedService.getAllBedsWithWard();
        List<Map<String, Object>> result = beds.stream().map(b -> Map.<String, Object>of(
                "id", b.getId(),
                "bedNumber", b.getBedNumber(),
                "status", b.getStatus().name(),
                "wardName", b.getWard().getName(),
                "wardType", b.getWard().getType().name()
        )).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/admit/{patientId}")
    public ResponseEntity<?> admit(@PathVariable Long patientId) {
        Admission admission = bedService.admitPatient(patientId);
        return ResponseEntity.ok(Map.of(
                "id", admission.getId(),
                "bedNumber", admission.getBed().getBedNumber(),
                "wardName", admission.getBed().getWard().getName(),
                "admittedAt", admission.getAdmittedAt().toString()
        ));
    }

    @PutMapping("/discharge/{admissionId}")
    public ResponseEntity<?> discharge(@PathVariable Long admissionId) {
        Admission admission = bedService.dischargePatient(admissionId);
        return ResponseEntity.ok(Map.of(
                "message", "Patient discharged successfully",
                "dischargedAt", admission.getDischargedAt().toString()
        ));
    }
}
