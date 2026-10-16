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

import com.msme.dto.snapshot.AssessmentInputResponse;
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

        if (!MSME_ACTIVE.equals(msme.getIsActive())) {
            throw new InvalidRequestException(
                "Financial data cannot be submitted "
                + "for an inactive MSME"
            );
        }

        LocalDate reportingPeriod =
                request.getReportingPeriod();

        validateReportingPeriod(reportingPeriod);
        validateFinancialValues(request);

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

        LocalDateTime now = LocalDateTime.now();

        FinancialSnapshot snapshot =
                new FinancialSnapshot();

        snapshot.setMsmeId(msmeId);
        snapshot.setSubmittedByUserId(
            principal.getUserId()
        );

        snapshot.setReportingPeriod(reportingPeriod);
        snapshot.setRevenue(request.getRevenue());
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
     * MSME REPRESENTATIVE - OWN FINANCIAL HISTORY
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
     * RM / RISK ADMIN - MSME FINANCIAL HISTORY
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
     * RISK ADMIN - ALL FINANCIAL SNAPSHOTS
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
     * SHARED - ONE SNAPSHOT FOR ONE REPORTING PERIOD
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
     * MSME REPRESENTATIVE - READINESS
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

        return buildReadinessResponse(
                msmeId,
                reportingPeriod
        );
    }

    /*
     * =========================================================
     * ALL THREE ROLES - ASSESSMENT READINESS
     *
     * MSME_REP:
     *      MSME is derived from JWT.
     *
     * RM:
     *      Requested MSME must belong to RM.
     *
     * RISK_ADMIN:
     *      Any MSME.
     *
     * Useful for enabling/disabling the Assessment button.
     * =========================================================
     */

    @Transactional(readOnly = true)
    @PreAuthorize(
        "hasAnyRole('RISK_ADMIN', 'RM', 'MSME_REP')"
    )
    public SnapshotReadinessResponse checkAssessmentReadiness(
            Long requestedMsmeId,
            LocalDate reportingPeriod) {

        CustomUserPrincipal principal =
                getCurrentPrincipal();

        validateReportingPeriod(reportingPeriod);

        Long authorizedMsmeId =
                resolveAuthorizedMsmeId(
                    principal,
                    requestedMsmeId
                );

        Msme msme =
                getMsmeOrThrow(authorizedMsmeId);

        authorizeMsmeAccess(
                principal,
                msme
        );

        return buildReadinessResponse(
                authorizedMsmeId,
                reportingPeriod
        );
    }

    /*
     * =========================================================
     * ASSESSMENT INPUT FOR MS2
     *
     * Returns:
     * - MSME details needed for calculation
     * - sanctioned credit limit
     * - period
     * - period - 1 month
     * - period - 2 months
     *
     * Authentication/authorization is based on the SAME
     * user's JWT forwarded by MS2.
     * =========================================================
     */

    @Transactional(readOnly = true)
    @PreAuthorize(
        "hasAnyRole('RISK_ADMIN', 'RM', 'MSME_REP')"
    )
    public AssessmentInputResponse getAssessmentInput(
            Long requestedMsmeId,
            LocalDate reportingPeriod) {

        CustomUserPrincipal principal =
                getCurrentPrincipal();

        validateReportingPeriod(reportingPeriod);

        /*
         * MSME_REP cannot select another MSME.
         *
         * RM and Risk Admin provide selected MSME ID,
         * but authorization is still performed below.
         */
        Long authorizedMsmeId =
                resolveAuthorizedMsmeId(
                    principal,
                    requestedMsmeId
                );

        Msme msme =
                getMsmeOrThrow(authorizedMsmeId);

        authorizeMsmeAccess(
                principal,
                msme
        );

        /*
         * Do not perform an assessment against an inactive MSME.
         */
        if (!MSME_ACTIVE.equals(msme.getIsActive())) {
            throw new InvalidRequestException(
                "Assessment cannot be performed "
                + "for an inactive MSME"
            );
        }

        LocalDate period0 =
                reportingPeriod;

        LocalDate period1 =
                reportingPeriod.minusMonths(1);

        LocalDate period2 =
                reportingPeriod.minusMonths(2);

        FinancialSnapshot current =
                getRequiredSnapshot(
                    authorizedMsmeId,
                    period0
                );

        FinancialSnapshot previous =
                getRequiredSnapshot(
                    authorizedMsmeId,
                    period1
                );

        FinancialSnapshot twoMonthsPrior =
                getRequiredSnapshot(
                    authorizedMsmeId,
                    period2
                );

        /*
         * Return chronologically:
         *
         * period-2
         * period-1
         * period
         *
         * This is convenient for MS2's calculation engine.
         */
        List<FinancialSnapshotResponse> snapshots =
                List.of(
                    toResponse(twoMonthsPrior),
                    toResponse(previous),
                    toResponse(current)
                );

        return new AssessmentInputResponse(
                msme.getMsmeId(),
                msme.getBusinessName(),
                msme.getSanctionedCreditLimit(),
                reportingPeriod,
                snapshots
        );
    }

    /*
     * =========================================================
     * READINESS BUILDER
     * =========================================================
     */

    private SnapshotReadinessResponse buildReadinessResponse(
            Long msmeId,
            LocalDate reportingPeriod) {

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
     * REQUIRED SNAPSHOT LOOKUP
     * =========================================================
     */

    private FinancialSnapshot getRequiredSnapshot(
            Long msmeId,
            LocalDate reportingPeriod) {

        return financialSnapshotRepository
                .findByMsmeIdAndReportingPeriod(
                    msmeId,
                    reportingPeriod
                )
                .orElseThrow(() ->
                    new InvalidRequestException(
                        "Assessment data is incomplete. "
                        + "Missing financial snapshot for "
                        + YearMonth.from(reportingPeriod)
                    )
                );
    }

    /*
     * =========================================================
     * RESOLVE MSME ACCORDING TO ROLE
     * =========================================================
     */

    private Long resolveAuthorizedMsmeId(
            CustomUserPrincipal principal,
            Long requestedMsmeId) {

        /*
         * MSME Representative:
         *
         * Ignore client-selected ownership.
         * MSME comes from authenticated principal.
         */
        if (ROLE_MSME_REP.equals(
                principal.getRole())) {

            Long principalMsmeId =
                    principal.getMsmeId();

            if (principalMsmeId == null) {
                throw new ForbiddenOperationException(
                    "No MSME is associated with this account"
                );
            }

            /*
             * If caller supplied an MSME ID, it must still
             * match their own MSME.
             */
            if (requestedMsmeId != null
                    && !principalMsmeId.equals(
                        requestedMsmeId
                    )) {

                throw new ForbiddenOperationException(
                    "You are not authorized to access "
                    + "this MSME"
                );
            }

            return principalMsmeId;
        }

        /*
         * RM and Risk Admin must select an MSME.
         */
        if (requestedMsmeId == null
                || requestedMsmeId <= 0) {

            throw new InvalidRequestException(
                "Valid MSME ID is required"
            );
        }

        return requestedMsmeId;
    }

    /*
     * =========================================================
     * FINANCIAL VALIDATION
     * =========================================================
     */

    private void validateFinancialValues(
            CreateFinancialSnapshotRequest request) {

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
         * RM:
         * requested MSME must actually be assigned to RM.
         */
        if (ROLE_RM.equals(role)) {

            verifyMsmeBelongsToRm(
                msme,
                principal.getUserId()
            );

            return;
        }

        /*
         * MSME Representative:
         * only own MSME.
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