package com.carechain.bed;

import com.carechain.audit.AuditEvent;
import com.carechain.audit.AuditMetadata;
import com.carechain.audit.AuditTrailService;
import com.carechain.bed.model.*;
import com.carechain.config.ApiErrorException;
import com.carechain.patient.PatientRepository;
import com.carechain.patient.model.Patient;
import com.carechain.realtime.RealtimeNotifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
@Service
public class BedService {

    @Autowired
    private BedRepository bedRepository;

    @Autowired
    private WardRepository wardRepository;

    @Autowired
    private AdmissionRepository admissionRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private RealtimeNotifier realtimeNotifier;

    @Autowired
    private AuditTrailService auditTrailService;

    public List<Ward> getWardAvailability() {
        return wardRepository.findAllByOrderByNameAsc();
    }

    public List<Bed> getAllBedsWithWard() {
        return bedRepository.findAllWithWard();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or hasRole('DOCTOR')")
    public Admission admitPatient(Long patientId) {
        Patient patient = patientRepository.findByIdForUpdate(patientId)
                .orElseThrow(() -> ApiErrorException.notFound("Patient not found"));

        // Check if patient is already admitted
        admissionRepository.findByPatientIdAndActiveRecordKey(patientId, Admission.ACTIVE_RECORD_KEY)
                .ifPresent(a -> {
                    throw ApiErrorException.conflict("Patient is already admitted in bed " + a.getBed().getBedNumber());
                });

        // Find first available bed
        Bed bed = bedRepository.findFirstByStatusOrderByIdAsc(BedStatus.AVAILABLE)
                .orElseThrow(() -> ApiErrorException.conflict("No beds available"));

        Ward ward = wardRepository.findByIdForUpdate(bed.getWard().getId())
                .orElseThrow(() -> ApiErrorException.notFound("Ward not found"));
        if (ward.getAvailableBeds() <= 0) {
            throw ApiErrorException.conflict("No beds available");
        }

        // Update bed status
        bed.setStatus(BedStatus.OCCUPIED);
        bedRepository.save(bed);

        // Update ward available count
        ward.setAvailableBeds(ward.getAvailableBeds() - 1);
        wardRepository.save(ward);

        // Create admission
        Admission admission = Admission.builder()
                .patient(patient)
                .bed(bed)
                .activeRecordKey(Admission.ACTIVE_RECORD_KEY)
                .build();

        try {
            Admission savedAdmission = admissionRepository.saveAndFlush(admission);
            realtimeNotifier.publishBedsRefresh("patient-admitted");
            realtimeNotifier.publishAdminRefresh("patient-admitted");
            auditTrailService.record(AuditEvent.builder()
                    .action("PATIENT_ADMITTED")
                    .resourceType("ADMISSION")
                    .resourceId(AuditMetadata.id(savedAdmission.getId()))
                    .details(AuditMetadata.map(
                            "patientId", patient.getId(),
                            "bedId", bed.getId(),
                            "bedNumber", bed.getBedNumber(),
                            "wardId", ward.getId(),
                            "wardName", ward.getName()))
                    .build());
            return savedAdmission;
        } catch (DataIntegrityViolationException exception) {
            throw ApiErrorException.conflict("Patient is already admitted or the selected bed is no longer available");
        }
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or hasRole('DOCTOR')")
    public Admission dischargePatient(Long admissionId) {
        Admission admission = admissionRepository.findByIdForUpdate(admissionId)
                .orElseThrow(() -> ApiErrorException.notFound("Admission not found"));

        if (admission.getDischargedAt() != null) {
            throw ApiErrorException.conflict("Patient already discharged");
        }

        Bed bed = bedRepository.findByIdForUpdate(admission.getBed().getId())
                .orElseThrow(() -> ApiErrorException.notFound("Bed not found"));
        Ward ward = wardRepository.findByIdForUpdate(bed.getWard().getId())
                .orElseThrow(() -> ApiErrorException.notFound("Ward not found"));

        admission.setDischargedAt(LocalDateTime.now());
        admission.setActiveRecordKey(null);
        admissionRepository.save(admission);

        // Free the bed
        bed.setStatus(BedStatus.AVAILABLE);
        bedRepository.save(bed);

        // Update ward available count
        ward.setAvailableBeds(Math.min(ward.getTotalBeds(), ward.getAvailableBeds() + 1));
        wardRepository.save(ward);

        realtimeNotifier.publishBedsRefresh("patient-discharged");
        realtimeNotifier.publishAdminRefresh("patient-discharged");
        auditTrailService.record(AuditEvent.builder()
                .action("PATIENT_DISCHARGED")
                .resourceType("ADMISSION")
                .resourceId(AuditMetadata.id(admission.getId()))
                .details(AuditMetadata.map(
                        "patientId", admission.getPatient().getId(),
                        "bedId", bed.getId(),
                        "bedNumber", bed.getBedNumber(),
                        "wardId", ward.getId(),
                        "dischargedAt", admission.getDischargedAt().toString()))
                .build());
        return admission;
    }

    public long countByStatus(BedStatus status) {
        return bedRepository.countByStatus(status);
    }

    public long countTotal() {
        return bedRepository.count();
    }
}
