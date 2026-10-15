
package com.msme.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.msme.dto.assessment.*;
import com.msme.service.AssessmentService;
import com.msme.service.AssessmentComparisonService;

@RestController
@RequestMapping("/api/assessment")
@Validated
@PreAuthorize("hasAnyRole('RISK_ADMIN', 'RM')")
public class AssessmentController {

    private final AssessmentService assessmentService;
    private final AssessmentComparisonService comparisonService;

    public AssessmentController(
            AssessmentService assessmentService,
            AssessmentComparisonService comparisonService) {

        this.assessmentService = assessmentService;
        this.comparisonService = comparisonService;
    }

    // POST /api/assessment/assessments
    @PostMapping("/assessments")
    public ResponseEntity<AssessmentResponse> createAssessment(
            @Valid @RequestBody CreateAssessmentRequest request) {

        AssessmentResponse response =
                assessmentService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // POST /api/assessment/assessments/from-finance
    @PostMapping("/assessments/from-finance")
    public ResponseEntity<AssessmentResponse> createFromFinance(
            @Valid @RequestBody AssessmentFromFinanceRequest request) {

        AssessmentResponse response =
                assessmentService.createFromFinance(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // GET /api/assessment/assessments/compare
    @GetMapping("/assessments/compare")
    public ResponseEntity<AssessmentComparisonResponse> compare(
            @RequestParam @Positive Long beforeId,
            @RequestParam @Positive Long afterId) {

        return ResponseEntity.ok(
                comparisonService.compare(beforeId, afterId)
        );
    }

    // GET /api/assessment/assessments/{id}
    @GetMapping("/assessments/{id}")
    public ResponseEntity<AssessmentResponse> getAssessment(
            @PathVariable @Positive Long id) {

        return ResponseEntity.ok(
                assessmentService.getById(id)
        );
    }

    // GET /api/assessment/assessments/{id}/indicators
    @GetMapping("/assessments/{id}/indicators")
    public ResponseEntity<List<IndicatorResultResponse>> getIndicators(
            @PathVariable @Positive Long id) {

        return ResponseEntity.ok(
                assessmentService.getIndicators(id)
        );
    }

    // GET /api/assessment/msmes/{msmeId}/assessments
    @GetMapping("/msmes/{msmeId}/assessments")
    public ResponseEntity<List<AssessmentResponse>> getHistory(
            @PathVariable @Positive Long msmeId,
            @RequestParam(defaultValue = "0")
            @Min(0) int page,
            @RequestParam(defaultValue = "10")
            @Min(1) @Max(100) int size) {

        return ResponseEntity.ok(
                assessmentService.getHistory(msmeId, page, size)
        );
    }

    // GET /api/assessment/msmes/{msmeId}/assessments/latest
    @GetMapping("/msmes/{msmeId}/assessments/latest")
    public ResponseEntity<AssessmentResponse> getLatest(
            @PathVariable @Positive Long msmeId) {

        return ResponseEntity.ok(
                assessmentService.getLatest(msmeId)
        );
    }
}
