package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.dto.auth.*;
import com.aquatrack.smartwaterbilling.entity.Apartment;
import com.aquatrack.smartwaterbilling.entity.Household;
import com.aquatrack.smartwaterbilling.entity.User;
import com.aquatrack.smartwaterbilling.entity.enums.Role;
import com.aquatrack.smartwaterbilling.exception.DuplicateEntryException;
import com.aquatrack.smartwaterbilling.exception.ResourceNotFoundException;
import com.aquatrack.smartwaterbilling.repository.ApartmentRepository;
import com.aquatrack.smartwaterbilling.repository.HouseholdRepository;
import com.aquatrack.smartwaterbilling.repository.UserRepository;
import com.aquatrack.smartwaterbilling.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user registration, login, and profile management.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ApartmentRepository apartmentRepository;
    private final HouseholdRepository householdRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    // ----------------------------------------------------------------
    // Register
    // ----------------------------------------------------------------

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEntryException(
                    "A user with email '" + request.getEmail() + "' already exists");
        }

        User.UserBuilder builder = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(request.getRole());

        if (request.getRole() == Role.ADMIN) {
            if (request.getApartmentId() == null) {
                throw new IllegalArgumentException("apartmentId is required for ADMIN role");
            }
            Apartment apartment = apartmentRepository.findById(request.getApartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Apartment", request.getApartmentId()));
            builder.apartment(apartment);
        } else {
            if (request.getHouseholdId() == null) {
                throw new IllegalArgumentException("householdId is required for RESIDENT role");
            }
            Household household = householdRepository.findById(request.getHouseholdId())
                    .orElseThrow(() -> new ResourceNotFoundException("Household", request.getHouseholdId()));
            builder.household(household);
        }

        User user = userRepository.save(builder.build());
        String token = jwtUtil.generateToken(user);

        return buildAuthResponse(user, token);
    }

    // ----------------------------------------------------------------
    // Login
    // ----------------------------------------------------------------

    public AuthResponse login(LoginRequest request) {
        // Throws BadCredentialsException if credentials are wrong
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with email: " + request.getEmail()));

        String token = jwtUtil.generateToken(user);
        return buildAuthResponse(user, token);
    }

    // ----------------------------------------------------------------
    // Profile — view
    // ----------------------------------------------------------------

    @Transactional(readOnly = true)
    public UserProfileDto getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with email: " + email));
        return toProfileDto(user);
    }

    // ----------------------------------------------------------------
    // Profile — update
    // ----------------------------------------------------------------

    @Transactional
    public UserProfileDto updateProfile(String currentEmail, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with email: " + currentEmail));

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getEmail() != null && !request.getEmail().equals(currentEmail)) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateEntryException(
                        "Email '" + request.getEmail() + "' is already in use");
            }
            user.setEmail(request.getEmail());
        }
        if (request.getPassword() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        return toProfileDto(userRepository.save(user));
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private AuthResponse buildAuthResponse(User user, String token) {
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresInMs(jwtUtil.getExpirationMs())
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .build();
    }

    private UserProfileDto toProfileDto(User user) {
        return UserProfileDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .apartmentId(user.getApartment() != null ? user.getApartment().getId() : null)
                .householdId(user.getHousehold() != null ? user.getHousehold().getId() : null)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
