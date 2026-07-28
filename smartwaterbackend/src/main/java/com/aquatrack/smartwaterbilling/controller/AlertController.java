package com.aquatrack.smartwaterbilling.controller;

import com.aquatrack.smartwaterbilling.dto.alert.AlertResponse;
import com.aquatrack.smartwaterbilling.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * In-app alert listing.
 *
 * <ul>
 *   <li>{@code GET /api/alerts?householdId=} — authenticated</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AlertResponse>> listByHousehold(@RequestParam Long householdId) {
        return ResponseEntity.ok(alertService.listByHousehold(householdId));
    }
}
