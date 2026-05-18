package com.carechain;

import com.carechain.audit.AuditTrailService;
import com.carechain.bed.AdmissionRepository;
import com.carechain.bed.BedRepository;
import com.carechain.bed.BedService;
import com.carechain.bed.WardRepository;
import com.carechain.bed.model.*;
import com.carechain.patient.PatientRepository;
import com.carechain.patient.model.Patient;
import com.carechain.realtime.RealtimeNotifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BedServiceTest {

    @InjectMocks
    private BedService bedService;

    @Mock
    private BedRepository bedRepository;

    @Mock
    private WardRepository wardRepository;

    @Mock
    private AdmissionRepository admissionRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private RealtimeNotifier realtimeNotifier;

    @Mock
    private AuditTrailService auditTrailService;

    @Test
    void getWardAvailability_shouldReturnAllWards() {
        Ward w1 = Ward.builder().id(1L).name("General A").type(WardType.GENERAL).totalBeds(20).availableBeds(15).build();
        Ward w2 = Ward.builder().id(2L).name("ICU").type(WardType.ICU).totalBeds(10).availableBeds(3).build();
        when(wardRepository.findAllByOrderByNameAsc()).thenReturn(List.of(w1, w2));

        List<Ward> result = bedService.getWardAvailability();

        assertEquals(2, result.size());
        assertEquals("General A", result.get(0).getName());
    }

    @Test
    void admitPatient_shouldAssignBedAndCreateAdmission() {
        Patient patient = Patient.builder().id(1L).name("John").build();
        Ward ward = Ward.builder().id(1L).name("General").totalBeds(20).availableBeds(10).build();
        Bed bed = Bed.builder().id(1L).ward(ward).bedNumber("G-01").status(BedStatus.AVAILABLE).build();

        when(patientRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(patient));
        when(admissionRepository.findByPatientIdAndActiveRecordKey(1L, Admission.ACTIVE_RECORD_KEY)).thenReturn(Optional.empty());
        when(bedRepository.findFirstByStatusOrderByIdAsc(BedStatus.AVAILABLE)).thenReturn(Optional.of(bed));
        when(wardRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(ward));
        when(bedRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(wardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(admissionRepository.saveAndFlush(any(Admission.class))).thenAnswer(inv -> {
            Admission a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });

        Admission result = bedService.admitPatient(1L);

        assertNotNull(result);
        assertEquals(BedStatus.OCCUPIED, bed.getStatus());
        assertEquals(9, ward.getAvailableBeds());
        verify(admissionRepository).saveAndFlush(any(Admission.class));
    }

    @Test
    void admitPatient_shouldThrowWhenAlreadyAdmitted() {
        Patient patient = Patient.builder().id(1L).name("John").build();
        Bed bed = Bed.builder().id(1L).bedNumber("G-01").build();
        Admission existing = Admission.builder().id(1L).patient(patient).bed(bed).build();

        when(patientRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(patient));
        when(admissionRepository.findByPatientIdAndActiveRecordKey(1L, Admission.ACTIVE_RECORD_KEY)).thenReturn(Optional.of(existing));

        assertThrows(RuntimeException.class, () -> bedService.admitPatient(1L));
    }

    @Test
    void admitPatient_shouldThrowWhenNoBedAvailable() {
        Patient patient = Patient.builder().id(1L).name("John").build();

        when(patientRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(patient));
        when(admissionRepository.findByPatientIdAndActiveRecordKey(1L, Admission.ACTIVE_RECORD_KEY)).thenReturn(Optional.empty());
        when(bedRepository.findFirstByStatusOrderByIdAsc(BedStatus.AVAILABLE)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> bedService.admitPatient(1L));
        assertEquals("No beds available", ex.getMessage());
    }

    @Test
    void dischargePatient_shouldFreeBedAndUpdateWard() {
        Ward ward = Ward.builder().id(1L).name("General").totalBeds(20).availableBeds(9).build();
        Bed bed = Bed.builder().id(1L).ward(ward).bedNumber("G-01").status(BedStatus.OCCUPIED).build();
        Patient patient = Patient.builder().id(1L).name("John").build();
        Admission admission = Admission.builder()
                .id(1L)
                .patient(patient)
                .bed(bed)
                .activeRecordKey(Admission.ACTIVE_RECORD_KEY)
                .build();

        when(admissionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(admission));
        when(bedRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bed));
        when(wardRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(ward));
        when(admissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(bedRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(wardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Admission result = bedService.dischargePatient(1L);

        assertNotNull(result.getDischargedAt());
        assertEquals(BedStatus.AVAILABLE, bed.getStatus());
        assertEquals(10, ward.getAvailableBeds());
    }

    @Test
    void dischargePatient_shouldThrowWhenAlreadyDischarged() {
        Admission admission = Admission.builder().id(1L).build();
        admission.setDischargedAt(java.time.LocalDateTime.now());

        when(admissionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(admission));

        assertThrows(RuntimeException.class, () -> bedService.dischargePatient(1L));
    }
}
