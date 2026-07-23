package com.aquatrack.smartwaterbilling.dto.apartment;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response body for apartment endpoints.
 */
@Data
@Builder
public class ApartmentResponse {

    private Long id;
    private String name;
    private String address;
    private Integer totalHouseholds;
    private String adminContact;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
