package com.carechain;

import com.carechain.auth.AuthService;
import com.carechain.auth.JwtUtil;
import com.carechain.auth.UserRepository;
import com.carechain.audit.AuditTrailService;
import com.carechain.auth.model.*;
import com.carechain.appointment.DoctorRepository;
import com.carechain.patient.PatientRepository;
import com.carechain.realtime.RealtimeNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RealtimeNotifier realtimeNotifier;

    @Mock
    private AuditTrailService auditTrailService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setEmail("patient@test.com");
        registerRequest.setPassword("password123");
        registerRequest.setName("John Doe");
        registerRequest.setAge(30);
        registerRequest.setBloodGroup("O+");
        registerRequest.setPhone("9876543210");

        loginRequest = new LoginRequest();
        loginRequest.setEmail("patient@test.com");
        loginRequest.setPassword("password123");
    }

    @Test
    void register_shouldCreateUserAndPatient() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(patientRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("mock-jwt-token");

        AuthResponse response = authService.register(registerRequest);

        assertNotNull(response);
        assertEquals("mock-jwt-token", response.getToken());
        assertEquals("patient@test.com", response.getEmail());
        assertEquals("PATIENT", response.getRole());
        verify(userRepository).saveAndFlush(any(User.class));
        verify(patientRepository).saveAndFlush(any());
    }

    @Test
    void register_shouldAlwaysCreatePatientRole() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(patientRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("mock-jwt-token");

        AuthResponse response = authService.register(registerRequest);

        assertEquals("PATIENT", response.getRole());
        verify(userRepository).saveAndFlush(argThat(user -> user.getRole() == Role.PATIENT));
    }

    @Test
    void register_shouldThrowWhenEmailExists() {
        when(userRepository.existsByEmail("patient@test.com")).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> authService.register(registerRequest));
        assertEquals("Email already registered", exception.getMessage());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void login_shouldReturnTokenOnValidCredentials() {
        User user = User.builder()
                .id(1L)
                .email("patient@test.com")
                .password("encodedPassword")
                .role(Role.PATIENT)
                .build();

        when(userRepository.findByEmail("patient@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encodedPassword")).thenReturn(true);
        when(jwtUtil.generateToken("patient@test.com", "PATIENT")).thenReturn("mock-jwt-token");

        AuthResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("mock-jwt-token", response.getToken());
        assertEquals("PATIENT", response.getRole());
    }

    @Test
    void login_shouldThrowOnInvalidEmail() {
        when(userRepository.findByEmail("patient@test.com")).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> authService.login(loginRequest));
        assertEquals("Invalid email or password", exception.getMessage());
    }

    @Test
    void login_shouldThrowOnWrongPassword() {
        User user = User.builder()
                .id(1L)
                .email("patient@test.com")
                .password("encodedPassword")
                .role(Role.PATIENT)
                .build();

        when(userRepository.findByEmail("patient@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encodedPassword")).thenReturn(false);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> authService.login(loginRequest));
        assertEquals("Invalid email or password", exception.getMessage());
    }
}
