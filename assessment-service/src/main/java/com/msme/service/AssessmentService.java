package com.msme.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.msme.calculator.AssessmentCalculationResult;
import com.msme.calculator.AssessmentCalculator;
import com.msme.client.FinancialSnapshotClient;
import com.msme.client.MsmeAccessClient;
import com.msme.dto.assessment.AssessmentFromFinanceRequest;
import com.msme.dto.assessment.AssessmentResponse;
import com.msme.dto.assessment.CreateAssessmentRequest;
import com.msme.dto.assessment.FinancialSnapshotRequest;
import com.msme.dto.assessment.IndicatorResultResponse;
import com.msme.entity.AssessmentIndicatorResult;
import com.msme.entity.RiskAssessment;
import com.msme.entity.ThresholdConfig;
import com.msme.exception.DuplicateAssessmentException;
import com.msme.exception.InvalidRequestException;
import com.msme.exception.ResourceNotFoundException;
import com.msme.repository.AssessmentIndicatorResultRepository;
import com.msme.repository.RiskAssessmentRepository;

@Service
public class AssessmentService {

    private final AssessmentCalculator calculator;
    private final FinancialSnapshotClient financeClient;
    private final ThresholdService thresholdService;
    private final RiskAssessmentRepository assessmentRepository;
    private final AssessmentIndicatorResultRepository indicatorRepository;
    private final TransactionTemplate transactionTemplate;
    private final MsmeAccessClient msmeAccessClient;
    private final com.msme.client.AssessmentWarningClient warningClient;

    public AssessmentService(
            AssessmentCalculator calculator,
            FinancialSnapshotClient financeClient,
            ThresholdService thresholdService,
            RiskAssessmentRepository assessmentRepository,
            AssessmentIndicatorResultRepository indicatorRepository,
            PlatformTransactionManager transactionManager,
            MsmeAccessClient msmeAccessClient,
            com.msme.client.AssessmentWarningClient warningClient) {

        this.calculator = calculator;
        this.financeClient = financeClient;
        this.thresholdService = thresholdService;
        this.assessmentRepository = assessmentRepository;
        this.indicatorRepository = indicatorRepository;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
        this.msmeAccessClient = msmeAccessClient;
        this.warningClient = warningClient;
    }

    public AssessmentResponse create(
            CreateAssessmentRequest request) {

        if (request == null
                || request.msmeId() == null
                || request.msmeId() <= 0
                || request.snapshots() == null
                || request.snapshots().size() != 3) {

            throw new InvalidRequestException(
                    "Exactly three snapshots and a valid MSME ID are required"
            );
        }

        // Authorize before calculation or returning an existing result.
        msmeAccessClient.checkAccess(request.msmeId());

        List<AssessmentCalculator.Snapshot> snapshots =
                request.snapshots()
                        .stream()
                        .map(this::toCalculatorSnapshot)
                        .toList();

        for (AssessmentCalculator.Snapshot snapshot : snapshots) {

            if (!Objects.equals(
                    request.msmeId(),
                    snapshot.msmeId())) {

                throw new InvalidRequestException(
                        "All snapshots must belong to the requested MSME"
                );
            }
        }

        AssessmentCalculator.Snapshot current = snapshots
                .stream()
                .max(Comparator.comparing(
                        AssessmentCalculator.Snapshot::reportingPeriod
                ))
                .orElseThrow();

        // Existing result is returned without recalculation.
        var existing = assessmentRepository.findBySnapshotId(
                current.snapshotId()
        );

        if (existing.isPresent()) {

            if (!existing.get().msmeId().equals(request.msmeId())) {
                throw new DuplicateAssessmentException(
                        "Snapshot belongs to a different MSME"
                );
            }

            return withWarning(getById(existing.get().assessmentId()));
        }

        ThresholdConfig threshold =
                thresholdService.getEffectiveThreshold(
                        request.msmeId(),
                        LocalDateTime.now()
                );

        AssessmentCalculator.Thresholds limits =
                new AssessmentCalculator.Thresholds(
                        threshold.revenueDeclinePct(),
                        threshold.overdueReceivablePct(),
                        threshold.creditUtilisationPct()
                );

        AssessmentCalculationResult calculated;

        try {
            calculated = calculator.calculate(snapshots, limits);

        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(
                    ex.getMessage(),
                    ex
            );
        }

        try {
            Long assessmentId = transactionTemplate.execute(status -> {

                // Serialize assessment creation/backfills for the same business.
                assessmentRepository.lockMsme(request.msmeId());
                // Recheck inside the transaction.
                var duplicate = assessmentRepository
                        .findBySnapshotId(current.snapshotId());

                if (duplicate.isPresent()) {
                    return duplicate.get().assessmentId();
                }

                Long previousId = null; // Set from reporting periods after insertion.

                RiskAssessment assessment = new RiskAssessment(
                        null,
                        request.msmeId(),
                        current.snapshotId(),
                        threshold.thresholdConfigId(),
                        null,
                        calculated.severity(),
                        calculated.stressPattern(),
                        previousId,
                        null
                );

                Long newId = assessmentRepository.save(assessment);

                List<AssessmentIndicatorResult> results =
                        calculated.indicators()
                                .stream()
                                .map(indicator ->
                                        new AssessmentIndicatorResult(
                                                null,
                                                newId,
                                                indicator.indicatorCode(),
                                                indicator.calculatedValue(),
                                                indicator.thresholdValue(),
                                                indicator.breached(),
                                                indicator.explanation()
                                        )
                                )
                                .toList();

                indicatorRepository.saveAll(results);
                assessmentRepository.repairChronology(request.msmeId());

                return newId;
            });

            if (assessmentId == null) {
                throw new IllegalStateException(
                        "Assessment transaction returned no ID"
                );
            }

            return withWarning(getById(assessmentId));

        } catch (DataIntegrityViolationException ex) {

            // The failed transaction has completed or rolled back.
            // The unique snapshot constraint prevents duplicate inserts.
            var duplicate = assessmentRepository.findBySnapshotId(
                    current.snapshotId()
            );

            if (duplicate.isPresent()) {
                return withWarning(getById(duplicate.get().assessmentId()));
            }

            throw new InvalidRequestException(
                    "Assessment could not be saved due to "
                            + "a database constraint violation",
                    ex
            );
        }
    }

    public AssessmentResponse createFromFinance(
            AssessmentFromFinanceRequest request) {

        if (request == null
                || request.msmeId() == null
                || request.msmeId() <= 0
                || request.reportingPeriod() == null
                || request.reportingPeriod().getDayOfMonth() != 1
                || !request.reportingPeriod().isBefore(java.time.LocalDate.now(
                        java.time.ZoneId.of("Asia/Kolkata")).withDayOfMonth(1))) {

            throw new InvalidRequestException(
                    "A valid MSME ID and completed reporting month are required"
            );
        }

        // MS1's assessment-input endpoint also validates MSME access.
        List<FinancialSnapshotRequest> snapshots =
                financeClient.getAssessmentSnapshots(
                        request.msmeId(),
                        request.reportingPeriod()
                );

        return create(new CreateAssessmentRequest(
                request.msmeId(),
                snapshots
        ));
    }

    public AssessmentResponse getById(Long id) {

        RiskAssessment assessment = getAssessmentOrThrow(id);

        msmeAccessClient.checkAccess(assessment.msmeId());

        return toAssessmentResponse(assessment);
    }

    private AssessmentResponse withWarning(AssessmentResponse response) {
        // This runs only after the assessment and all indicators have committed.
        if (response.severity() != com.msme.enums.Severity.HEALTHY)
            warningClient.process(response.assessmentId());
        return response;
    }

    public List<IndicatorResultResponse> getIndicators(
            Long assessmentId) {

        RiskAssessment assessment =
                getAssessmentOrThrow(assessmentId);

        msmeAccessClient.checkAccess(assessment.msmeId());

        return loadIndicators(assessmentId);
    }

    public AssessmentResponse getLatest(Long msmeId) {

        msmeAccessClient.checkAccess(msmeId);

        RiskAssessment assessment = assessmentRepository
                .findLatestByMsmeId(msmeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Latest assessment for MSME",
                                msmeId
                        )
                );

        return toAssessmentResponse(assessment);
    }

    public List<AssessmentResponse> getHistory(
            Long msmeId,
            int page,
            int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException(
                    "Page must be >= 0 and size must be 1-100"
            );
        }

        msmeAccessClient.checkAccess(msmeId);

        return assessmentRepository
                .findHistory(msmeId, page, size)
                .stream()
                .map(this::toAssessmentResponse)
                .toList();
    }

    public long countHistory(Long msmeId) {

        msmeAccessClient.checkAccess(msmeId);

        return assessmentRepository.countByMsmeId(msmeId);
    }

    private RiskAssessment getAssessmentOrThrow(Long id) {

        if (id == null || id <= 0) {
            throw new InvalidRequestException(
                    "Valid assessment ID is required"
            );
        }

        return assessmentRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Assessment",
                                id
                        )
                );
    }

    private List<IndicatorResultResponse> loadIndicators(
            Long assessmentId) {

        return indicatorRepository
                .findByAssessmentId(assessmentId)
                .stream()
                .map(this::toIndicatorResponse)
                .toList();
    }

    private AssessmentResponse toAssessmentResponse(
            RiskAssessment assessment) {

        return new AssessmentResponse(
                assessment.assessmentId(),
                assessment.msmeId(),
                assessment.snapshotId(),
                assessment.thresholdConfigId(),
                assessment.assessmentDate(),
                assessment.severity(),
                assessment.stressPattern(),
                assessment.previousAssessmentId(),
                loadIndicators(assessment.assessmentId()),
                assessmentRepository.reportingPeriod(assessment.assessmentId())
        );
    }

    private AssessmentCalculator.Snapshot toCalculatorSnapshot(
            FinancialSnapshotRequest snapshot) {

        if (snapshot == null) {
            throw new InvalidRequestException(
                    "Snapshot cannot be null"
            );
        }

        return new AssessmentCalculator.Snapshot(
                snapshot.snapshotId(),
                snapshot.msmeId(),
                snapshot.reportingPeriod(),
                snapshot.revenue(),
                snapshot.totalReceivables(),
                snapshot.overdueReceivables(),
                snapshot.creditUsed(),
                snapshot.sanctionedCreditLimit()
        );
    }

    private IndicatorResultResponse toIndicatorResponse(
            AssessmentIndicatorResult result) {

        return new IndicatorResultResponse(
                result.indicatorResultId(),
                result.indicatorCode(),
                result.calculatedValue(),
                result.thresholdValueUsed(),
                result.breached(),
                result.explanationText()
        );
    }
}
