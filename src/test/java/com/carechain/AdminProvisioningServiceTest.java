package com.carechain;

import com.carechain.admin.AdminProvisioningService;
import com.carechain.admin.CreateDoctorRequest;
import com.carechain.admin.CreateWardRequest;
import com.carechain.audit.AuditTrailService;
import com.carechain.appointment.DoctorRepository;
import com.carechain.appointment.model.Doctor;
import com.carechain.auth.UserRepository;
import com.carechain.auth.model.Role;
import com.carechain.auth.model.User;
import com.carechain.bed.BedRepository;
import com.carechain.bed.WardRepository;
import com.carechain.bed.model.Bed;
import com.carechain.bed.model.BedStatus;
import com.carechain.bed.model.Ward;
import com.carechain.bed.model.WardType;
import com.carechain.realtime.RealtimeNotifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminProvisioningServiceTest {

    @InjectMocks
    private AdminProvisioningService adminProvisioningService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private WardRepository wardRepository;

    @Mock
    private BedRepository bedRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RealtimeNotifier realtimeNotifier;

    @Mock
    private AuditTrailService auditTrailService;

    @Test
    void createDoctor_shouldProvisionDoctorUser() {
        CreateDoctorRequest request = new CreateDoctorRequest(
                "Doctor@Test.com",
                "password123",
                "Dr. Alice",
                "Cardiology",
                "mon,wed,mon,fri".toUpperCase()
        );

        when(userRepository.existsByEmail("doctor@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return user;
        });
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(invocation -> {
            Doctor doctor = invocation.getArgument(0);
            doctor.setId(5L);
            return doctor;
        });

        Doctor doctor = adminProvisioningService.createDoctor(request);

        assertEquals("doctor@test.com", doctor.getUser().getEmail());
        assertEquals(Role.DOCTOR, doctor.getUser().getRole());
        assertEquals("MON,WED,FRI", doctor.getAvailableDays());
    }

    @Test
    void createWard_shouldProvisionBeds() {
        CreateWardRequest request = new CreateWardRequest("Emergency Ward", WardType.EMERGENCY, 3, "ER");
        when(wardRepository.saveAndFlush(any(Ward.class))).thenAnswer(invocation -> {
            Ward ward = invocation.getArgument(0);
            ward.setId(7L);
            return ward;
        });

        adminProvisioningService.createWard(request);

        ArgumentCaptor<List<Bed>> bedsCaptor = ArgumentCaptor.forClass(List.class);
        verify(bedRepository).saveAllAndFlush(bedsCaptor.capture());
        List<Bed> beds = bedsCaptor.getValue();
        assertEquals(3, beds.size());
        assertEquals("ER-01", beds.get(0).getBedNumber());
        assertEquals(BedStatus.AVAILABLE, beds.get(0).getStatus());
        assertTrue(beds.stream().allMatch(bed -> bed.getWard().getId().equals(7L)));
    }

    @Test
    void ensureBootstrapAdmin_shouldCreateAdminWhenConfigured() {
        ReflectionTestUtils.setField(adminProvisioningService, "bootstrapAdminEmail", "admin@carechain.com");
        ReflectionTestUtils.setField(adminProvisioningService, "bootstrapAdminPassword", "password123");
        ReflectionTestUtils.setField(adminProvisioningService, "bootstrapAdminName", "CareChain Admin");
        when(userRepository.existsByEmail("admin@carechain.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(99L);
            return user;
        });

        adminProvisioningService.ensureBootstrapAdmin();

        verify(userRepository).saveAndFlush(any(User.class));
    }
}
