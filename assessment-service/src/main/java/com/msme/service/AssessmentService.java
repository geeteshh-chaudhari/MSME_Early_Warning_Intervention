
package com.msme.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.msme.calculator.AssessmentCalculator;
import com.msme.calculator.AssessmentCalculationResult;
import com.msme.client.FinancialSnapshotClient;
import com.msme.dto.assessment.*;
import com.msme.entity.*;
import com.msme.exception.*;
import com.msme.repository.*;

@Service
public class AssessmentService {

    private final AssessmentCalculator calculator;
    private final FinancialSnapshotClient financeClient;
    private final ThresholdService thresholdService;
    private final RiskAssessmentRepository assessmentRepository;
    private final AssessmentIndicatorResultRepository indicatorRepository;
    private final TransactionTemplate transactionTemplate;

    public AssessmentService(
            AssessmentCalculator calculator,
            FinancialSnapshotClient financeClient,
            ThresholdService thresholdService,
            RiskAssessmentRepository assessmentRepository,
            AssessmentIndicatorResultRepository indicatorRepository,
            PlatformTransactionManager transactionManager) {

        this.calculator = calculator;
        this.financeClient = financeClient;
        this.thresholdService = thresholdService;
        this.assessmentRepository = assessmentRepository;
        this.indicatorRepository = indicatorRepository;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
    }

    public AssessmentResponse create(
            CreateAssessmentRequest request) {

        if (request == null || request.msmeId() == null
                || request.msmeId() <= 0
                || request.snapshots() == null
                || request.snapshots().size() != 3) {
            throw new InvalidRequestException(
                    "Exactly three snapshots and a valid MSME ID are required");
        }

        List<AssessmentCalculator.Snapshot> snapshots =
                request.snapshots().stream()
                    .map(this::toCalculatorSnapshot)
                    .toList();

        for (AssessmentCalculator.Snapshot snapshot : snapshots) {
            if (!Objects.equals(
                    request.msmeId(), snapshot.msmeId())) {
                throw new InvalidRequestException(
                        "All snapshots must belong to the requested MSME");
            }
        }

        AssessmentCalculator.Snapshot current = snapshots
                .stream()
                .max(java.util.Comparator.comparing(
                        AssessmentCalculator.Snapshot::reportingPeriod))
                .orElseThrow();

        // Existing result is returned without recalculation.
        var existing = assessmentRepository.findBySnapshotId(
                current.snapshotId());

        if (existing.isPresent()) {
            if (!existing.get().msmeId().equals(request.msmeId())) {
                throw new DuplicateAssessmentException(
                        "Snapshot belongs to a different MSME");
            }
            return getById(existing.get().assessmentId());
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
            throw new InvalidRequestException(ex.getMessage(), ex);
        }

        try {
            Long assessmentId = transactionTemplate.execute(status -> {

                // Recheck inside the transaction.
                var duplicate = assessmentRepository
                        .findBySnapshotId(current.snapshotId());

                if (duplicate.isPresent()) {
                    return duplicate.get().assessmentId();
                }

                Long previousId = assessmentRepository
                        .findLatestByMsmeId(request.msmeId())
                        .map(RiskAssessment::assessmentId)
                        .orElse(null);

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

                return newId;
            });

            if (assessmentId == null) {
                throw new IllegalStateException(
                        "Assessment transaction returned no ID");
            }

            return getById(assessmentId);

        } catch (DataIntegrityViolationException ex) {

            // The failed transaction has completed/rolled back.
            // Oracle's unique snapshot constraint protects
            // against concurrent duplicate inserts.
            var duplicate = assessmentRepository.findBySnapshotId(
                    current.snapshotId());

            if (duplicate.isPresent()) {
                return getById(duplicate.get().assessmentId());
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

        if (request == null || request.msmeId() == null
                || request.msmeId() <= 0) {
            throw new InvalidRequestException(
                    "Valid MSME ID is required");
        }

        List<FinancialSnapshotRequest> snapshots =
                financeClient.getLatestThreeSnapshots(
                    request.msmeId());

        return create(new CreateAssessmentRequest(
                request.msmeId(),
                snapshots
        ));
    }

    public AssessmentResponse getById(Long id) {

        RiskAssessment assessment =
                assessmentRepository.findById(id)
                    .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Assessment", id));

        List<IndicatorResultResponse> indicators =
                getIndicators(id);

        return new AssessmentResponse(
                assessment.assessmentId(),
                assessment.msmeId(),
                assessment.snapshotId(),
                assessment.thresholdConfigId(),
                assessment.assessmentDate(),
                assessment.severity(),
                assessment.stressPattern(),
                assessment.previousAssessmentId(),
                indicators
        );
    }

    public List<IndicatorResultResponse> getIndicators(
            Long assessmentId) {

        if (assessmentRepository.findById(assessmentId).isEmpty()) {
            throw new ResourceNotFoundException(
                    "Assessment", assessmentId);
        }

        return indicatorRepository
                .findByAssessmentId(assessmentId)
                .stream()
                .map(this::toIndicatorResponse)
                .toList();
    }

    public AssessmentResponse getLatest(Long msmeId) {

        RiskAssessment assessment =
                assessmentRepository.findLatestByMsmeId(msmeId)
                    .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Latest assessment for MSME",
                                msmeId));

        return getById(assessment.assessmentId());
    }

    public List<AssessmentResponse> getHistory(
            Long msmeId,
            int page,
            int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException(
                    "Page must be >= 0 and size must be 1-100");
        }

        return assessmentRepository
                .findHistory(msmeId, page, size)
                .stream()
                .map(a -> getById(a.assessmentId()))
                .toList();
    }

    public long countHistory(Long msmeId) {
        return assessmentRepository.countByMsmeId(msmeId);
    }

    private AssessmentCalculator.Snapshot toCalculatorSnapshot(
            FinancialSnapshotRequest s) {

        if (s == null) {
            throw new InvalidRequestException(
                    "Snapshot cannot be null");
        }

        return new AssessmentCalculator.Snapshot(
                s.snapshotId(),
                s.msmeId(),
                s.reportingPeriod(),
                s.revenue(),
                s.totalReceivables(),
                s.overdueReceivables(),
                s.creditUsed(),
                s.sanctionedCreditLimit()
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
