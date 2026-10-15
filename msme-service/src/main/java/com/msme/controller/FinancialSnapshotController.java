package com.msme.controller;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.msme.dto.snapshot.CreateFinancialSnapshotRequest;
import com.msme.dto.snapshot.FinancialSnapshotResponse;
import com.msme.dto.snapshot.SnapshotReadinessResponse;
import com.msme.service.FinancialSnapshotService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/snapshots")
public class FinancialSnapshotController {

    private final FinancialSnapshotService financialSnapshotService;

    public FinancialSnapshotController(
            FinancialSnapshotService financialSnapshotService) {

        this.financialSnapshotService =
                financialSnapshotService;
    }

    /*
     * =========================================================
     * MSME REPRESENTATIVE - SUBMIT FINANCIAL SNAPSHOT
     * =========================================================
     */

    /**
     * MSME Representative:
     *
     * Submit monthly financial data for their own MSME.
     *
     * MSME ID and submitted-by user ID are obtained from
     * the authenticated user and are not accepted from
     * the request body.
     */
    @PostMapping
    public ResponseEntity<FinancialSnapshotResponse>
            createSnapshot(
                    @Valid
                    @RequestBody
                    CreateFinancialSnapshotRequest request) {

        FinancialSnapshotResponse createdSnapshot =
                financialSnapshotService
                    .createSnapshot(request);

        URI location = URI.create(
                "/api/snapshots/msme/"
                + createdSnapshot.getMsmeId()
                + "/"
                + createdSnapshot.getReportingPeriod()
        );

        return ResponseEntity
                .created(location)
                .body(createdSnapshot);
    }

    /*
     * =========================================================
     * MSME REPRESENTATIVE - OWN FINANCIAL HISTORY
     * =========================================================
     */

    /**
     * MSME Representative:
     *
     * View all financial snapshots belonging to
     * their own MSME.
     *
     * Results are ordered by reporting period.
     */
    @GetMapping("/my")
    public ResponseEntity<List<FinancialSnapshotResponse>>
            getMySnapshots() {

        List<FinancialSnapshotResponse> snapshots =
                financialSnapshotService
                    .getMySnapshots();

        return ResponseEntity.ok(snapshots);
    }

    /*
     * =========================================================
     * RM / RISK ADMIN - MSME FINANCIAL HISTORY
     * =========================================================
     */

    /**
     * RM:
     * View financial history only for an MSME
     * assigned to the logged-in RM.
     *
     * Risk Admin:
     * View financial history for any MSME.
     */
    @GetMapping("/msme/{msmeId}")
    public ResponseEntity<List<FinancialSnapshotResponse>>
            getSnapshotsByMsme(
                    @PathVariable Long msmeId) {

        List<FinancialSnapshotResponse> snapshots =
                financialSnapshotService
                    .getSnapshotsByMsme(msmeId);

        return ResponseEntity.ok(snapshots);
    }

    /*
     * =========================================================
     * RISK ADMIN - ALL FINANCIAL SNAPSHOTS
     * =========================================================
     */

    /**
     * Risk Admin:
     *
     * View all financial snapshots across all MSMEs.
     */
    @GetMapping
    public ResponseEntity<List<FinancialSnapshotResponse>>
            getAllSnapshots() {

        List<FinancialSnapshotResponse> snapshots =
                financialSnapshotService
                    .getAllSnapshots();

        return ResponseEntity.ok(snapshots);
    }

    /*
     * =========================================================
     * SHARED - ONE SNAPSHOT FOR ONE REPORTING PERIOD
     * =========================================================
     */

    /**
     * Risk Admin:
     *      Can view any MSME snapshot.
     *
     * RM:
     *      Can view snapshots only for assigned MSMEs.
     *
     * MSME Representative:
     *      Can view snapshots only for their own MSME.
     *
     * Reporting period format:
     *
     * 2026-04-01
     *
     * The date must represent the first day of the
     * reporting month.
     */
    @GetMapping("/msme/{msmeId}/period/{reportingPeriod}")
    public ResponseEntity<FinancialSnapshotResponse>
            getSnapshotByPeriod(
                    @PathVariable Long msmeId,

                    @PathVariable
                    @DateTimeFormat(
                        iso = DateTimeFormat.ISO.DATE
                    )
                    LocalDate reportingPeriod) {

        FinancialSnapshotResponse snapshot =
                financialSnapshotService
                    .getSnapshotByPeriod(
                        msmeId,
                        reportingPeriod
                    );

        return ResponseEntity.ok(snapshot);
    }

    /*
     * =========================================================
     * MSME REPRESENTATIVE - ASSESSMENT READINESS
     * =========================================================
     */

    /**
     * MSME Representative:
     *
     * Check whether the current reporting period and
     * the previous two consecutive reporting periods
     * are available.
     *
     * IMPORTANT:
     *
     * This does NOT perform risk assessment.
     * It only checks whether the required financial
     * snapshot history exists.
     *
     * Example:
     *
     * /api/snapshots/readiness/2026-04-01
     */
    @GetMapping("/readiness/{reportingPeriod}")
    public ResponseEntity<SnapshotReadinessResponse>
            checkReadiness(

                    @PathVariable
                    @DateTimeFormat(
                        iso = DateTimeFormat.ISO.DATE
                    )
                    LocalDate reportingPeriod) {

        SnapshotReadinessResponse response =
                financialSnapshotService
                    .checkReadiness(reportingPeriod);

        return ResponseEntity.ok(response);
    }
}