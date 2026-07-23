package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.dto.auth.LoginRequest;
import com.aquatrack.smartwaterbilling.dto.auth.RegisterRequest;
import com.aquatrack.smartwaterbilling.entity.Apartment;
import com.aquatrack.smartwaterbilling.entity.User;
import com.aquatrack.smartwaterbilling.entity.enums.Role;
import com.aquatrack.smartwaterbilling.exception.DuplicateEntryException;
import com.aquatrack.smartwaterbilling.exception.ResourceNotFoundException;
import com.aquatrack.smartwaterbilling.repository.ApartmentRepository;
import com.aquatrack.smartwaterbilling.repository.HouseholdRepository;
import com.aquatrack.smartwaterbilling.repository.UserRepository;
import com.aquatrack.smartwaterbilling.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService unit tests")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ApartmentRepository apartmentRepository;
    @Mock private HouseholdRepository householdRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private Apartment apartment;

    @BeforeEach
    void setUp() {
        apartment = Apartment.builder()
                .id(1L)
                .name("Test Towers")
                .address("123 Main St")
                .totalHouseholds(10)
                .adminContact("admin@test.com")
                .build();
    }

    // ----------------------------------------------------------------
    // Register — success (ADMIN)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("register ADMIN — creates user and returns JWT token")
    void register_admin_success() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Alice Admin");
        req.setEmail("alice@example.com");
        req.setPassword("secret123");
        req.setRole(Role.ADMIN);
        req.setApartmentId(1L);

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(apartmentRepository.findById(1L)).thenReturn(Optional.of(apartment));
        when(passwordEncoder.encode("secret123")).thenReturn("$bcrypt$hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtUtil.generateToken(any())).thenReturn("mocked.jwt.token");
        when(jwtUtil.getExpirationMs()).thenReturn(86400000L);

        var response = authService.register(req);

        assertThat(response.getToken()).isEqualTo("mocked.jwt.token");
        assertThat(response.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository).save(any(User.class));
    }

    // ----------------------------------------------------------------
    // Register — duplicate email → DuplicateEntryException
    // ----------------------------------------------------------------

    @Test
    @DisplayName("register — duplicate email throws DuplicateEntryException")
    void register_duplicateEmail_throws() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("duplicate@example.com");
        req.setPassword("pass1234");
        req.setRole(Role.ADMIN);
        req.setApartmentId(1L);

        when(userRepository.existsByEmail("duplicate@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(DuplicateEntryException.class)
                .hasMessageContaining("duplicate@example.com");
    }

    // ----------------------------------------------------------------
    // Register — ADMIN without apartmentId → IllegalArgumentException
    // ----------------------------------------------------------------

    @Test
    @DisplayName("register ADMIN without apartmentId throws IllegalArgumentException")
    void register_adminNoApartment_throws() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("admin2@example.com");
        req.setPassword("pass1234");
        req.setRole(Role.ADMIN);
        // No apartmentId set

        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apartmentId");
    }

    // ----------------------------------------------------------------
    // Register — apartment not found → ResourceNotFoundException
    // ----------------------------------------------------------------

    @Test
    @DisplayName("register ADMIN with non-existent apartment throws ResourceNotFoundException")
    void register_adminBadApartment_throws() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("admin3@example.com");
        req.setPassword("pass1234");
        req.setRole(Role.ADMIN);
        req.setApartmentId(999L);

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(apartmentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ----------------------------------------------------------------
    // Login — success
    // ----------------------------------------------------------------

    @Test
    @DisplayName("login — valid credentials returns token")
    void login_success() {
        LoginRequest req = new LoginRequest();
        req.setEmail("bob@example.com");
        req.setPassword("password");

        User user = User.builder()
                .id(2L)
                .email("bob@example.com")
                .passwordHash("$bcrypt$hash")
                .role(Role.RESIDENT)
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null); // successful auth returns Authentication object; we don't need it
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user)).thenReturn("bob.jwt.token");
        when(jwtUtil.getExpirationMs()).thenReturn(3600000L);

        var response = authService.login(req);

        assertThat(response.getToken()).isEqualTo("bob.jwt.token");
        assertThat(response.getEmail()).isEqualTo("bob@example.com");
    }

    // ----------------------------------------------------------------
    // Login — bad credentials → BadCredentialsException propagates
    // ----------------------------------------------------------------

    @Test
    @DisplayName("login — bad credentials throws BadCredentialsException")
    void login_badCredentials_throws() {
        LoginRequest req = new LoginRequest();
        req.setEmail("nobody@example.com");
        req.setPassword("wrong");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BadCredentialsException.class);
    }
}
