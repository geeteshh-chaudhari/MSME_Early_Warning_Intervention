package com.msme.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.msme.dto.snapshot.AssessmentInputResponse;
import com.msme.dto.snapshot.SnapshotReadinessResponse;
import com.msme.service.FinancialSnapshotService;

@RestController
@RequestMapping("/api/assessment-data")
public class AssessmentDataController {

    private final FinancialSnapshotService financialSnapshotService;

    public AssessmentDataController(
            FinancialSnapshotService financialSnapshotService) {

        this.financialSnapshotService =
                financialSnapshotService;
    }

    /*
     * =========================================================
     * ASSESSMENT READINESS
     *
     * Available to:
     * - RISK_ADMIN
     * - RM
     * - MSME_REP
     *
     * Security is enforced inside FinancialSnapshotService.
     *
     * RM:
     * requested MSME must be assigned to logged-in RM.
     *
     * MSME_REP:
     * requested MSME must be the representative's own MSME.
     *
     * RISK_ADMIN:
     * may check any MSME.
     *
     * Example:
     *
     * GET
     * /api/assessment-data/msmes/10/readiness/2026-04-01
     * =========================================================
     */

    @GetMapping(
        "/msmes/{msmeId}/readiness/{reportingPeriod}"
    )
    public ResponseEntity<SnapshotReadinessResponse>
            checkAssessmentReadiness(

                    @PathVariable Long msmeId,

                    @PathVariable
                    @DateTimeFormat(
                        iso = DateTimeFormat.ISO.DATE
                    )
                    LocalDate reportingPeriod) {

        SnapshotReadinessResponse response =
                financialSnapshotService
                    .checkAssessmentReadiness(
                        msmeId,
                        reportingPeriod
                    );

        return ResponseEntity.ok(response);
    }

    /*
     * =========================================================
     * ASSESSMENT INPUT
     *
     * Intended primarily for Assessment Service (MS2).
     *
     * Returns:
     *
     * - MSME ID
     * - business name
     * - sanctioned credit limit
     * - requested reporting period
     * - snapshot for period - 2
     * - snapshot for period - 1
     * - snapshot for period
     *
     * MS2 must forward the original logged-in user's JWT
     * when calling this endpoint.
     *
     * MS1 validates that JWT and performs MSME ownership
     * authorization before returning financial data.
     *
     * Example:
     *
     * GET
     * /api/assessment-data/msmes/10/input/2026-04-01
     * =========================================================
     */

    @GetMapping(
        "/msmes/{msmeId}/input/{reportingPeriod}"
    )
    public ResponseEntity<AssessmentInputResponse>
            getAssessmentInput(

                    @PathVariable Long msmeId,

                    @PathVariable
                    @DateTimeFormat(
                        iso = DateTimeFormat.ISO.DATE
                    )
                    LocalDate reportingPeriod) {

        AssessmentInputResponse response =
                financialSnapshotService
                    .getAssessmentInput(
                        msmeId,
                        reportingPeriod
                    );

        return ResponseEntity.ok(response);
    }
}