
package com.msme.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.msme.dto.threshold.CreateThresholdRequest;
import com.msme.dto.threshold.ReviewThresholdRequest;
import com.msme.dto.threshold.ThresholdResponse;
import com.msme.exception.InvalidRequestException;
import com.msme.service.ThresholdService;

@RestController
@RequestMapping("/api/assessment")
@Validated
public class ThresholdController {

    private final ThresholdService thresholdService;

    private final String adminUsername;
    private final Long adminUserId;

    public ThresholdController(
            ThresholdService thresholdService,
            @Value("${msme.security.admin.username}")
            String adminUsername,
            @Value("${msme.security.admin.user-id}")
            Long adminUserId) {

        this.thresholdService = thresholdService;
        this.adminUsername = adminUsername;
        this.adminUserId = adminUserId;
    }

    @PostMapping("/thresholds")
    @PreAuthorize("hasRole('RISK_ADMIN')")
    public ResponseEntity<ThresholdResponse> createThreshold(
            @Valid @RequestBody CreateThresholdRequest request,
            Authentication authentication) {

        Long userId = resolveAdminUserId(authentication);

        ThresholdResponse response =
                thresholdService.create(request, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/thresholds/{id}/review")
    @PreAuthorize("hasRole('RISK_ADMIN')")
    public ResponseEntity<ThresholdResponse> reviewThreshold(
            @PathVariable @Positive Long id,
            @Valid @RequestBody ReviewThresholdRequest request,
            Authentication authentication) {

        Long userId = resolveAdminUserId(authentication);

        ThresholdResponse response =
                thresholdService.review(id, request, userId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/thresholds/{id}")
    @PreAuthorize("hasAnyRole('RISK_ADMIN', 'RM')")
    public ResponseEntity<ThresholdResponse> getThreshold(
            @PathVariable @Positive Long id) {

        return ResponseEntity.ok(
                thresholdService.getById(id)
        );
    }

    @GetMapping("/msmes/{msmeId}/thresholds")
    @PreAuthorize("hasAnyRole('RISK_ADMIN', 'RM')")
    public ResponseEntity<List<ThresholdResponse>> getHistory(
            @PathVariable @Positive Long msmeId,
            @RequestParam(defaultValue = "0")
            @Min(0) int page,
            @RequestParam(defaultValue = "10")
            @Min(1) @Max(100) int size) {

        return ResponseEntity.ok(
                thresholdService.getHistory(msmeId, page, size)
        );
    }

    private Long resolveAdminUserId(
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new InvalidRequestException(
                    "Authenticated Risk Admin is required");
        }

        if (!adminUsername.equals(authentication.getName())) {
            throw new InvalidRequestException(
                    "Authenticated user has no configured "
                    + "APP_USER audit mapping");
        }

        return adminUserId;
    }
}
