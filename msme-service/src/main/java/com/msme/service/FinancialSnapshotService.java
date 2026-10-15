package com.msme.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.msme.dto.snapshot.CreateFinancialSnapshotRequest;
import com.msme.dto.snapshot.FinancialSnapshotResponse;
import com.msme.dto.snapshot.SnapshotReadinessResponse;
import com.msme.entity.FinancialSnapshot;
import com.msme.entity.Msme;
import com.msme.exception.DuplicateResourceException;
import com.msme.exception.ForbiddenOperationException;
import com.msme.exception.InvalidRequestException;
import com.msme.exception.ResourceNotFoundException;
import com.msme.repository.FinancialSnapshotRepository;
import com.msme.repository.MsmeRepository;
import com.msme.security.CustomUserPrincipal;

@Service
@Transactional
public class FinancialSnapshotService {

    private static final String ROLE_RISK_ADMIN = "RISK_ADMIN";
    private static final String ROLE_RM = "RM";
    private static final String ROLE_MSME_REP = "MSME_REP";

    private static final String MSME_ACTIVE = "Y";

    private final FinancialSnapshotRepository financialSnapshotRepository;
    private final MsmeRepository msmeRepository;

    public FinancialSnapshotService(
            FinancialSnapshotRepository financialSnapshotRepository,
            MsmeRepository msmeRepository) {

        this.financialSnapshotRepository =
                financialSnapshotRepository;

        this.msmeRepository =
                msmeRepository;
    }

    /*
     * =========================================================
     * MSME REPRESENTATIVE - SUBMIT MONTHLY FINANCIAL SNAPSHOT
     * =========================================================
     */

    @PreAuthorize("hasRole('MSME_REP')")
    public FinancialSnapshotResponse createSnapshot(
            CreateFinancialSnapshotRequest request) {

        CustomUserPrincipal principal =
                getCurrentPrincipal();

        Long msmeId = principal.getMsmeId();

        if (msmeId == null) {
            throw new ForbiddenOperationException(
                "No MSME is associated with this account"
            );
        }

        Msme msme = getMsmeOrThrow(msmeId);

        /*
         * Financial data cannot be submitted for
         * a deactivated MSME.
         */
        if (!MSME_ACTIVE.equals(msme.getIsActive())) {
            throw new InvalidRequestException(
                "Financial data cannot be submitted "
                + "for an inactive MSME"
            );
        }

        LocalDate reportingPeriod =
                request.getReportingPeriod();

        /*
         * Database requires reporting period to represent
         * the first day of the reporting month.
         *
         * Example:
         * April 2026 -> 2026-04-01
         */
        validateReportingPeriod(reportingPeriod);

        /*
         * Cross-field financial validation.
         */
        validateFinancialValues(request);

        /*
         * Prevent duplicate monthly snapshot.
         *
         * Oracle UNIQUE(msme_id, reporting_period)
         * remains the final database-level protection.
         */
        if (financialSnapshotRepository
                .existsByMsmeIdAndReportingPeriod(
                    msmeId,
                    reportingPeriod
                )) {

            throw new DuplicateResourceException(
                "A financial snapshot already exists "
                + "for reporting period "
                + YearMonth.from(reportingPeriod)
            );
        }

        LocalDateTime now =
                LocalDateTime.now();

        FinancialSnapshot snapshot =
                new FinancialSnapshot();

        /*
         * MSME and submitting user come from authenticated
         * identity, not from request body.
         */
        snapshot.setMsmeId(msmeId);

        snapshot.setSubmittedByUserId(
            principal.getUserId()
        );

        snapshot.setReportingPeriod(
            reportingPeriod
        );

        snapshot.setRevenue(
            request.getRevenue()
        );

        snapshot.setTotalReceivables(
            request.getTotalReceivables()
        );

        snapshot.setOverdueReceivables(
            request.getOverdueReceivables()
        );

        snapshot.setCreditUsed(
            request.getCreditUsed()
        );

        snapshot.setSubmittedAt(now);
        snapshot.setCreatedAt(now);
        snapshot.setUpdatedAt(now);

        FinancialSnapshot savedSnapshot =
                financialSnapshotRepository.save(snapshot);

        return toResponse(savedSnapshot);
    }

    /*
     * =========================================================
     * MSME REPRESENTATIVE - VIEW OWN FINANCIAL HISTORY
     * =========================================================
     */

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MSME_REP')")
    public List<FinancialSnapshotResponse> getMySnapshots() {

        CustomUserPrincipal principal =
                getCurrentPrincipal();

        Long msmeId = principal.getMsmeId();

        if (msmeId == null) {
            throw new ForbiddenOperationException(
                "No MSME is associated with this account"
            );
        }

        getMsmeOrThrow(msmeId);

        return financialSnapshotRepository
                .findByMsmeIdOrderByReportingPeriodAsc(msmeId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /*
     * =========================================================
     * RM / RISK ADMIN - VIEW MSME FINANCIAL HISTORY
     * =========================================================
     */

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('RM', 'RISK_ADMIN')")
    public List<FinancialSnapshotResponse> getSnapshotsByMsme(
            Long msmeId) {

        CustomUserPrincipal principal =
                getCurrentPrincipal();

        Msme msme =
                getMsmeOrThrow(msmeId);

        /*
         * Risk Admin can view any MSME.
         *
         * RM can view only MSMEs assigned to that RM.
         */
        if (ROLE_RM.equals(principal.getRole())) {

            verifyMsmeBelongsToRm(
                msme,
                principal.getUserId()
            );
        }

        return financialSnapshotRepository
                .findByMsmeIdOrderByReportingPeriodAsc(msmeId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /*
     * =========================================================
     * RISK ADMIN - VIEW ALL FINANCIAL SNAPSHOTS
     * =========================================================
     */

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('RISK_ADMIN')")
    public List<FinancialSnapshotResponse> getAllSnapshots() {

        return financialSnapshotRepository
                .findAllByOrderByReportingPeriodAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /*
     * =========================================================
     * VIEW ONE MSME SNAPSHOT FOR ONE REPORTING PERIOD
     *
     * RISK_ADMIN:
     *      any MSME
     *
     * RM:
     *      assigned MSME only
     *
     * MSME_REP:
     *      own MSME only
     * =========================================================
     */

    @Transactional(readOnly = true)
    @PreAuthorize(
        "hasAnyRole('RISK_ADMIN', 'RM', 'MSME_REP')"
    )
    public FinancialSnapshotResponse getSnapshotByPeriod(
            Long msmeId,
            LocalDate reportingPeriod) {

        CustomUserPrincipal principal =
                getCurrentPrincipal();

        validateReportingPeriod(reportingPeriod);

        Msme msme =
                getMsmeOrThrow(msmeId);

        authorizeMsmeAccess(
                principal,
                msme
        );

        FinancialSnapshot snapshot =
                financialSnapshotRepository
                    .findByMsmeIdAndReportingPeriod(
                        msmeId,
                        reportingPeriod
                    )
                    .orElseThrow(() ->
                        new ResourceNotFoundException(
                            "Financial snapshot not found for MSME "
                            + msmeId
                            + " and reporting period "
                            + YearMonth.from(reportingPeriod)
                        )
                    );

        return toResponse(snapshot);
    }

    /*
     * =========================================================
     * MSME REPRESENTATIVE - ASSESSMENT READINESS
     *
     * IMPORTANT:
     * This does NOT perform risk assessment.
     *
     * It checks only whether:
     *
     * current month
     * previous month
     * two months prior
     *
     * are all present.
     * =========================================================
     */

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MSME_REP')")
    public SnapshotReadinessResponse checkReadiness(
            LocalDate reportingPeriod) {

        CustomUserPrincipal principal =
                getCurrentPrincipal();

        Long msmeId =
                principal.getMsmeId();

        if (msmeId == null) {
            throw new ForbiddenOperationException(
                "No MSME is associated with this account"
            );
        }

        getMsmeOrThrow(msmeId);

        validateReportingPeriod(reportingPeriod);

        LocalDate currentPeriod =
                reportingPeriod;

        LocalDate previousPeriod =
                reportingPeriod.minusMonths(1);

        LocalDate twoPeriodsPrior =
                reportingPeriod.minusMonths(2);

        boolean currentAvailable =
                financialSnapshotRepository
                    .existsByMsmeIdAndReportingPeriod(
                        msmeId,
                        currentPeriod
                    );

        boolean previousAvailable =
                financialSnapshotRepository
                    .existsByMsmeIdAndReportingPeriod(
                        msmeId,
                        previousPeriod
                    );

        boolean twoPeriodsPriorAvailable =
                financialSnapshotRepository
                    .existsByMsmeIdAndReportingPeriod(
                        msmeId,
                        twoPeriodsPrior
                    );

        boolean assessmentReady =
                currentAvailable
                && previousAvailable
                && twoPeriodsPriorAvailable;

        List<String> missingPeriods =
                new ArrayList<>();

        if (!twoPeriodsPriorAvailable) {
            missingPeriods.add(
                YearMonth.from(twoPeriodsPrior)
                    .toString()
            );
        }

        if (!previousAvailable) {
            missingPeriods.add(
                YearMonth.from(previousPeriod)
                    .toString()
            );
        }

        if (!currentAvailable) {
            missingPeriods.add(
                YearMonth.from(currentPeriod)
                    .toString()
            );
        }

        return new SnapshotReadinessResponse(
                YearMonth.from(currentPeriod).toString(),
                currentAvailable,
                previousAvailable,
                twoPeriodsPriorAvailable,
                assessmentReady,
                missingPeriods
        );
    }

    /*
     * =========================================================
     * FINANCIAL VALIDATION
     * =========================================================
     */

    private void validateFinancialValues(
            CreateFinancialSnapshotRequest request) {

        /*
         * @DecimalMin already checks individual values
         * for negative numbers.
         *
         * This is the cross-field rule from the DB schema:
         *
         * overdue_receivables <= total_receivables
         */
        if (request.getOverdueReceivables()
                .compareTo(
                    request.getTotalReceivables()
                ) > 0) {

            throw new InvalidRequestException(
                "Overdue receivables cannot exceed "
                + "total receivables"
            );
        }
    }

    /*
     * =========================================================
     * REPORTING PERIOD VALIDATION
     * =========================================================
     */

    private void validateReportingPeriod(
            LocalDate reportingPeriod) {

        if (reportingPeriod == null) {
            throw new InvalidRequestException(
                "Reporting period is required"
            );
        }

        /*
         * Database rule:
         *
         * reporting_period =
         * TRUNC(reporting_period, 'MM')
         */
        if (reportingPeriod.getDayOfMonth() != 1) {

            throw new InvalidRequestException(
                "Reporting period must be the first "
                + "day of the reporting month"
            );
        }
    }

    /*
     * =========================================================
     * AUTHORIZATION
     * =========================================================
     */

    private void authorizeMsmeAccess(
            CustomUserPrincipal principal,
            Msme msme) {

        String role =
                principal.getRole();

        /*
         * Risk Admin has global read visibility.
         */
        if (ROLE_RISK_ADMIN.equals(role)) {
            return;
        }

        /*
         * RM can access only assigned MSMEs.
         */
        if (ROLE_RM.equals(role)) {

            verifyMsmeBelongsToRm(
                msme,
                principal.getUserId()
            );

            return;
        }

        /*
         * MSME Representative can access only
         * their own MSME.
         */
        if (ROLE_MSME_REP.equals(role)) {

            Long principalMsmeId =
                    principal.getMsmeId();

            if (principalMsmeId == null
                    || !principalMsmeId.equals(
                        msme.getMsmeId()
                    )) {

                throw new ForbiddenOperationException(
                    "You are not authorized to access "
                    + "financial data for this MSME"
                );
            }

            return;
        }

        throw new ForbiddenOperationException(
            "You are not authorized to access "
            + "financial snapshot information"
        );
    }

    private void verifyMsmeBelongsToRm(
            Msme msme,
            Long rmId) {

        if (!rmId.equals(
                msme.getAssignedRmId()
        )) {

            throw new ForbiddenOperationException(
                "You are not authorized to access "
                + "financial data for this MSME"
            );
        }
    }

    /*
     * =========================================================
     * ENTITY LOOKUP
     * =========================================================
     */

    private Msme getMsmeOrThrow(
            Long msmeId) {

        return msmeRepository.findById(msmeId)
                .orElseThrow(() ->
                    new ResourceNotFoundException(
                        "MSME not found with id: "
                        + msmeId
                    )
                );
    }

    /*
     * =========================================================
     * CURRENT AUTHENTICATED USER
     * =========================================================
     */

    private CustomUserPrincipal getCurrentPrincipal() {

        Authentication authentication =
                SecurityContextHolder
                    .getContext()
                    .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                    instanceof CustomUserPrincipal)) {

            throw new AuthenticationCredentialsNotFoundException(
                "Authentication is required"
            );
        }

        return (CustomUserPrincipal)
                authentication.getPrincipal();
    }

    /*
     * =========================================================
     * ENTITY -> RESPONSE DTO
     * =========================================================
     */

    private FinancialSnapshotResponse toResponse(
            FinancialSnapshot snapshot) {

        return new FinancialSnapshotResponse(
                snapshot.getSnapshotId(),
                snapshot.getMsmeId(),
                snapshot.getReportingPeriod(),
                snapshot.getRevenue(),
                snapshot.getTotalReceivables(),
                snapshot.getOverdueReceivables(),
                snapshot.getCreditUsed(),
                snapshot.getSubmittedByUserId(),
                snapshot.getSubmittedAt(),
                snapshot.getCreatedAt(),
                snapshot.getUpdatedAt()
        );
    }
}