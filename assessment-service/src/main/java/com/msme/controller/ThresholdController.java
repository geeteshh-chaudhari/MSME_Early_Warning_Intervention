package com.msme.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.msme.dto.threshold.CreateThresholdRequest;
import com.msme.dto.threshold.ReviewThresholdRequest;
import com.msme.dto.threshold.ThresholdResponse;
import com.msme.security.CustomUserPrincipal;
import com.msme.service.ThresholdService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/assessment")
@Validated
public class ThresholdController {

    private final ThresholdService thresholdService;

    public ThresholdController(
            ThresholdService thresholdService) {

        this.thresholdService = thresholdService;
    }

    @PostMapping("/thresholds")
    @PreAuthorize("hasRole('RISK_ADMIN')")
    public ResponseEntity<ThresholdResponse> createThreshold(
            @Valid @RequestBody CreateThresholdRequest request,
            Authentication authentication) {

        Long userId = resolveAdminUserId(authentication);

        ThresholdResponse response = thresholdService.create(
                request,
                userId
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PreAuthorize("hasRole('RISK_ADMIN')")
    public ResponseEntity<ThresholdResponse> reviewThreshold(
            @PathVariable @Positive Long id,
            @Valid @RequestBody ReviewThresholdRequest request,
            Authentication authentication) {

        Long userId = resolveAdminUserId(authentication);

        ThresholdResponse response = thresholdService.review(
                id,
                request,
                userId
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/thresholds/{id}")
    @PreAuthorize("hasAnyRole('RISK_ADMIN', 'RM', 'MSME_REP')")
    public ResponseEntity<ThresholdResponse> getThreshold(
            @PathVariable @Positive Long id) {

        return ResponseEntity.ok(
                thresholdService.getById(id)
        );
    }

    @GetMapping("/msmes/{msmeId}/thresholds")
    @PreAuthorize("hasAnyRole('RISK_ADMIN', 'RM', 'MSME_REP')")
    public ResponseEntity<List<ThresholdResponse>> getHistory(
            @PathVariable @Positive Long msmeId,

            @RequestParam(defaultValue = "0")
            @Min(0) int page,

            @RequestParam(defaultValue = "10")
            @Min(1) @Max(100) int size) {

        return ResponseEntity.ok(
                thresholdService.getHistory(
                        msmeId,
                        page,
                        size
                )
        );
    }

    private Long resolveAdminUserId(
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                    instanceof CustomUserPrincipal principal)) {

            throw new AuthenticationCredentialsNotFoundException(
                    "JWT authentication is required"
            );
        }

        if (!"RISK_ADMIN".equals(principal.getRole())) {
            throw new AccessDeniedException(
                    "Risk Admin access is required"
            );
        }

        return principal.getUserId();
    }
}