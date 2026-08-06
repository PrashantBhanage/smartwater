package com.aquatrack.smartwaterbilling.controller;

import com.aquatrack.smartwaterbilling.dto.alert.AlertResponse;
import com.aquatrack.smartwaterbilling.entity.User;
import com.aquatrack.smartwaterbilling.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping("/alerts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AlertResponse>> listByHousehold(@RequestParam Long householdId) {
        return ResponseEntity.ok(alertService.listByHousehold(householdId));
    }

    @GetMapping("/apartments/{apartmentId}/alerts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AlertResponse>> getApartmentAlerts(
            @PathVariable Long apartmentId,
            @RequestParam(name = "days", defaultValue = "30") int days) {
        return ResponseEntity.ok(alertService.listRecentAlertsForApartment(apartmentId, days));
    }

    @GetMapping("/residents/my-alerts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AlertResponse>> getMyAlerts(
            @AuthenticationPrincipal User currentUser) {
        if (currentUser.getHousehold() == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(alertService.listByHousehold(currentUser.getHousehold().getId()));
    }
}
