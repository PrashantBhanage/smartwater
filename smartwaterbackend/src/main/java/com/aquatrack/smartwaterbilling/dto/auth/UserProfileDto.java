package com.aquatrack.smartwaterbilling.dto.auth;

import com.aquatrack.smartwaterbilling.entity.enums.Role;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Profile view for GET /api/auth/me
 */
@Data
@Builder
public class UserProfileDto {

    private Long id;
    private String email;
    private String fullName;
    private Role role;
    private Long apartmentId;
    private Long householdId;
    private LocalDateTime createdAt;
}
