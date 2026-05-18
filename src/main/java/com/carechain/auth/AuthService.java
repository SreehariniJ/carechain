package com.carechain.auth;

import com.carechain.audit.AuditEvent;
import com.carechain.audit.AuditMetadata;
import com.carechain.audit.AuditOutcome;
import com.carechain.audit.AuditTrailService;
import com.carechain.auth.model.*;
import com.carechain.config.ApiErrorException;
import com.carechain.patient.PatientRepository;
import com.carechain.patient.model.Patient;
import com.carechain.realtime.RealtimeNotifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RealtimeNotifier realtimeNotifier;

    @Autowired
    private AuditTrailService auditTrailService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(email)) {
            auditTrailService.record(AuditEvent.builder()
                    .action("PATIENT_REGISTRATION_FAILED")
                    .resourceType("USER")
                    .resourceId(email)
                    .outcome(AuditOutcome.FAILURE)
                    .actorEmailOverride(email)
                    .actorRoleOverride("ANONYMOUS")
                    .details(AuditMetadata.map(
                            "reason", "EMAIL_ALREADY_REGISTERED",
                            "registrationType", "SELF_SERVICE"))
                    .build());
            throw ApiErrorException.conflict("Email already registered");
        }

        User user;
        try {
            user = userRepository.saveAndFlush(User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(request.getPassword()))
                    .role(Role.PATIENT)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            auditTrailService.record(AuditEvent.builder()
                    .action("PATIENT_REGISTRATION_FAILED")
                    .resourceType("USER")
                    .resourceId(email)
                    .outcome(AuditOutcome.FAILURE)
                    .actorEmailOverride(email)
                    .actorRoleOverride("ANONYMOUS")
                    .details(AuditMetadata.map(
                            "reason", "EMAIL_ALREADY_REGISTERED",
                            "registrationType", "SELF_SERVICE"))
                    .build());
            throw ApiErrorException.conflict("Email already registered");
        }

        Patient patient = Patient.builder()
                .user(user)
                .name(request.getName())
                .age(request.getAge())
                .bloodGroup(request.getBloodGroup())
                .phone(request.getPhone())
                .build();
        Patient savedPatient = patientRepository.saveAndFlush(patient);
        realtimeNotifier.publishAdminRefresh("patient-registered");
        auditTrailService.record(AuditEvent.builder()
                .action("PATIENT_REGISTERED")
                .resourceType("PATIENT")
                .resourceId(AuditMetadata.id(savedPatient.getId()))
                .actorEmailOverride(user.getEmail())
                .actorRoleOverride(user.getRole().name())
                .details(AuditMetadata.map(
                        "userId", user.getId(),
                        "registrationType", "SELF_SERVICE"))
                .build());

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    auditTrailService.record(AuditEvent.builder()
                            .action("LOGIN_FAILED")
                            .resourceType("USER")
                            .resourceId(email)
                            .outcome(AuditOutcome.FAILURE)
                            .actorEmailOverride(email)
                            .actorRoleOverride("ANONYMOUS")
                            .details(AuditMetadata.map(
                                    "method", "PASSWORD",
                                    "reason", "INVALID_CREDENTIALS"))
                            .build());
                    return ApiErrorException.unauthorized("Invalid email or password");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            auditTrailService.record(AuditEvent.builder()
                    .action("LOGIN_FAILED")
                    .resourceType("USER")
                    .resourceId(email)
                    .outcome(AuditOutcome.FAILURE)
                    .actorEmailOverride(email)
                    .actorRoleOverride("ANONYMOUS")
                    .details(AuditMetadata.map(
                            "method", "PASSWORD",
                            "reason", "INVALID_CREDENTIALS"))
                    .build());
            throw ApiErrorException.unauthorized("Invalid email or password");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        auditTrailService.record(AuditEvent.builder()
                .action("LOGIN_SUCCESS")
                .resourceType("USER")
                .resourceId(AuditMetadata.id(user.getId()))
                .actorEmailOverride(user.getEmail())
                .actorRoleOverride(user.getRole().name())
                .details(AuditMetadata.map("method", "PASSWORD"))
                .build());

        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
