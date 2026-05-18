package com.carechain.admin;

import com.carechain.audit.AuditEvent;
import com.carechain.audit.AuditMetadata;
import com.carechain.audit.AuditTrailService;
import com.carechain.appointment.DoctorRepository;
import com.carechain.appointment.model.Doctor;
import com.carechain.config.ApiErrorException;
import com.carechain.auth.UserRepository;
import com.carechain.auth.model.Role;
import com.carechain.auth.model.User;
import com.carechain.bed.BedRepository;
import com.carechain.bed.WardRepository;
import com.carechain.bed.model.Bed;
import com.carechain.bed.model.BedStatus;
import com.carechain.bed.model.Ward;
import com.carechain.realtime.RealtimeNotifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AdminProvisioningService {

    private static final Set<String> VALID_DAYS = Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private WardRepository wardRepository;

    @Autowired
    private BedRepository bedRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RealtimeNotifier realtimeNotifier;

    @Autowired
    private AuditTrailService auditTrailService;

    @Value("${carechain.bootstrap.admin.email:}")
    private String bootstrapAdminEmail;

    @Value("${carechain.bootstrap.admin.password:}")
    private String bootstrapAdminPassword;

    @Value("${carechain.bootstrap.admin.name:CareChain Admin}")
    private String bootstrapAdminName;

    @Transactional
    public Doctor createDoctor(CreateDoctorRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw ApiErrorException.conflict("Email already registered");
        }

        User user;
        try {
            user = userRepository.saveAndFlush(User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(request.getPassword()))
                    .role(Role.DOCTOR)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            throw ApiErrorException.conflict("Email already registered");
        }

        Doctor doctor = Doctor.builder()
                .user(user)
                .name(request.getName().trim())
                .specialization(request.getSpecialization().trim())
                .availableDays(normalizeAvailableDays(request.getAvailableDays()))
                .build();

        Doctor savedDoctor = doctorRepository.save(doctor);
        realtimeNotifier.publishAdminRefresh("doctor-created");
        auditTrailService.record(AuditEvent.builder()
                .action("DOCTOR_CREATED")
                .resourceType("DOCTOR")
                .resourceId(AuditMetadata.id(savedDoctor.getId()))
                .details(AuditMetadata.map(
                        "doctorEmail", savedDoctor.getUser().getEmail(),
                        "specialization", savedDoctor.getSpecialization(),
                        "availableDays", savedDoctor.getAvailableDays()))
                .build());
        return savedDoctor;
    }

    @Transactional
    public Ward createWard(CreateWardRequest request) {
        Ward ward;
        try {
            ward = wardRepository.saveAndFlush(Ward.builder()
                    .name(request.getName().trim())
                    .type(request.getType())
                    .totalBeds(request.getTotalBeds())
                    .availableBeds(request.getTotalBeds())
                    .build());
        } catch (DataIntegrityViolationException exception) {
            throw ApiErrorException.conflict("Ward name already exists");
        }

        String prefix = resolveBedPrefix(request);
        List<Bed> beds = new ArrayList<>(request.getTotalBeds());
        for (int index = 1; index <= request.getTotalBeds(); index++) {
            beds.add(Bed.builder()
                    .ward(ward)
                    .bedNumber(formatBedNumber(prefix, index))
                    .status(BedStatus.AVAILABLE)
                    .build());
        }
        try {
            bedRepository.saveAllAndFlush(beds);
        } catch (DataIntegrityViolationException exception) {
            throw ApiErrorException.conflict("Bed prefix generates duplicate bed numbers");
        }

        realtimeNotifier.publishBedsRefresh("ward-created");
        realtimeNotifier.publishAdminRefresh("ward-created");
        auditTrailService.record(AuditEvent.builder()
                .action("WARD_CREATED")
                .resourceType("WARD")
                .resourceId(AuditMetadata.id(ward.getId()))
                .details(AuditMetadata.map(
                        "wardName", ward.getName(),
                        "wardType", ward.getType().name(),
                        "totalBeds", ward.getTotalBeds(),
                        "bedPrefix", prefix))
                .build());
        return ward;
    }

    @Transactional
    public void ensureBootstrapAdmin() {
        String email = normalizeEmail(bootstrapAdminEmail);
        if (email == null || email.isBlank() || bootstrapAdminPassword == null || bootstrapAdminPassword.isBlank()) {
            return;
        }

        if (userRepository.existsByEmail(email)) {
            return;
        }

        try {
            User admin = userRepository.saveAndFlush(User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(bootstrapAdminPassword))
                    .role(Role.ADMIN)
                    .build());
            if (admin != null) {
                auditTrailService.record(AuditEvent.builder()
                        .action("BOOTSTRAP_ADMIN_CREATED")
                        .resourceType("USER")
                        .resourceId(AuditMetadata.id(admin.getId()))
                        .actorEmailOverride("system@carechain")
                        .actorRoleOverride("SYSTEM")
                        .details(AuditMetadata.map("adminEmail", email))
                        .build());
            }
        } catch (DataIntegrityViolationException exception) {
            // Another instance may have created the same bootstrap user concurrently.
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeAvailableDays(String availableDays) {
        String[] days = availableDays.split(",");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String day : days) {
            String value = day.trim().toUpperCase(Locale.ROOT);
            if (!VALID_DAYS.contains(value)) {
                throw ApiErrorException.badRequest("Invalid day: " + day);
            }
            normalized.add(value);
        }
        return String.join(",", normalized);
    }

    private String resolveBedPrefix(CreateWardRequest request) {
        if (request.getBedPrefix() != null && !request.getBedPrefix().isBlank()) {
            return request.getBedPrefix().trim().toUpperCase(Locale.ROOT);
        }

        String sanitized = request.getName().replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (sanitized.isBlank()) {
            return request.getType().name().substring(0, Math.min(3, request.getType().name().length()));
        }
        return sanitized.substring(0, Math.min(6, sanitized.length()));
    }

    private String formatBedNumber(String prefix, int index) {
        return prefix + "-" + String.format("%02d", index);
    }
}
