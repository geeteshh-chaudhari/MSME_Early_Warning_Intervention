package com.msme;

import com.msme.calculator.AssessmentCalculator;
import com.msme.client.FinancialSnapshotClient;
import com.msme.client.MsmeAccessClient;
import com.msme.dto.assessment.AssessmentFromFinanceRequest;
import com.msme.dto.assessment.AssessmentResponse;
import com.msme.dto.assessment.CreateAssessmentRequest;
import com.msme.dto.assessment.FinancialSnapshotRequest;
import com.msme.entity.AssessmentIndicatorResult;
import com.msme.entity.RiskAssessment;
import com.msme.entity.ThresholdConfig;
import com.msme.enums.IndicatorCode;
import com.msme.enums.Severity;
import com.msme.enums.StressPattern;
import com.msme.enums.ThresholdStatus;
import com.msme.exception.DuplicateAssessmentException;
import com.msme.exception.InvalidRequestException;
import com.msme.exception.ResourceNotFoundException;
import com.msme.repository.AssessmentIndicatorResultRepository;
import com.msme.repository.RiskAssessmentRepository;
import com.msme.service.AssessmentService;
import com.msme.service.ThresholdService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {

    @Mock
    private FinancialSnapshotClient financeClient;

    @Mock
    private MsmeAccessClient accessClient;

    @Mock
    private ThresholdService thresholdService;

    @Mock
    private RiskAssessmentRepository assessmentRepository;

    @Mock
    private AssessmentIndicatorResultRepository indicatorRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private AssessmentService service;

    @BeforeEach
    void setUp() {
        service = new AssessmentService(
                new AssessmentCalculator(),
                financeClient,
                thresholdService,
                assessmentRepository,
                indicatorRepository,
                transactionManager,
                accessClient
        );
    }

    @Test
    void createSavesAssessmentAndThreeCalculatedIndicators() {
        prepareThreshold();
        prepareTransaction();

        when(assessmentRepository.save(any(RiskAssessment.class)))
                .thenReturn(1L);
        when(assessmentRepository.findById(1L))
                .thenReturn(Optional.of(savedAssessment(100L)));

        AssessmentResponse result = service.create(validRequest());

        ArgumentCaptor<RiskAssessment> assessmentCaptor =
                ArgumentCaptor.forClass(RiskAssessment.class);

        verify(assessmentRepository).save(assessmentCaptor.capture());

        RiskAssessment saved = assessmentCaptor.getValue();

        assertAll(
                () -> assertEquals(100L, saved.msmeId()),
                () -> assertEquals(3L, saved.snapshotId()),
                () -> assertEquals(5L, saved.thresholdConfigId()),
                () -> assertEquals(Severity.HIGH_PRIORITY, saved.severity()),
                () -> assertEquals(
                        StressPattern.BROAD_FINANCIAL_DETERIORATION,
                        saved.stressPattern()),
                () -> assertNull(saved.previousAssessmentId()),
                () -> assertEquals(1L, result.assessmentId())
        );

        verify(indicatorRepository).saveAll(argThat(results ->
                results.size() == 3
                        && results.stream().allMatch(
                                item -> Long.valueOf(1L)
                                        .equals(item.assessmentId()))
                        && results.stream().allMatch(
                                AssessmentIndicatorResult::breached)
                        && results.stream().anyMatch(item ->
                                item.indicatorCode()
                                        == IndicatorCode.REVENUE_DETERIORATION
                                && item.calculatedValue()
                                        .compareTo(bd("25")) == 0)
        ));

        verify(transactionManager).commit(any());
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void createLinksPreviousAssessment() {
        prepareThreshold();
        prepareTransaction();

        RiskAssessment previous = new RiskAssessment(
                99L, 100L, 2L, 5L, LocalDateTime.now(),
                Severity.WATCH, null, null, LocalDateTime.now()
        );

        when(assessmentRepository.findLatestByMsmeId(100L))
                .thenReturn(Optional.of(previous));
        when(assessmentRepository.save(any(RiskAssessment.class)))
                .thenReturn(1L);
        when(assessmentRepository.findById(1L))
                .thenReturn(Optional.of(savedAssessment(100L)));

        service.create(validRequest());

        verify(assessmentRepository).save(argThat(assessment ->
                Long.valueOf(99L).equals(
                        assessment.previousAssessmentId())));
    }

    @Test
    void createRejectsIncompleteRequest() {
        CreateAssessmentRequest incomplete =
                new CreateAssessmentRequest(100L, List.of());

        assertThrows(InvalidRequestException.class,
                () -> service.create(incomplete));

        verifyNoInteractions(
                accessClient, thresholdService, assessmentRepository);
    }

    @Test
    void createStopsWhenAccessIsDenied() {
        doThrow(new InvalidRequestException("Access denied"))
                .when(accessClient).checkAccess(100L);

        assertThrows(InvalidRequestException.class,
                () -> service.create(validRequest()));

        verifyNoInteractions(
                thresholdService, assessmentRepository,
                indicatorRepository, transactionManager);
    }

    @Test
    void createRejectsSnapshotsFromAnotherMsme() {
        List<FinancialSnapshotRequest> snapshots = List.of(
                snapshot(1L, 100L, 7, "100"),
                snapshot(2L, 100L, 8, "80"),
                snapshot(3L, 999L, 9, "60")
        );

        assertThrows(InvalidRequestException.class,
                () -> service.create(
                        new CreateAssessmentRequest(100L, snapshots)));

        verifyNoInteractions(
                thresholdService, assessmentRepository, transactionManager);
    }

    @Test
    void existingAssessmentIsReturnedWithoutRecalculation() {
        when(assessmentRepository.findBySnapshotId(3L))
                .thenReturn(Optional.of(savedAssessment(100L)));
        when(assessmentRepository.findById(1L))
                .thenReturn(Optional.of(savedAssessment(100L)));

        AssessmentResponse result = service.create(validRequest());

        assertEquals(1L, result.assessmentId());

        verify(assessmentRepository, never())
                .save(any(RiskAssessment.class));
        verifyNoInteractions(thresholdService, transactionManager);
    }

    @Test
    void existingSnapshotForDifferentMsmeIsRejected() {
        when(assessmentRepository.findBySnapshotId(3L))
                .thenReturn(Optional.of(savedAssessment(999L)));

        assertThrows(DuplicateAssessmentException.class,
                () -> service.create(validRequest()));

        verify(assessmentRepository, never())
                .save(any(RiskAssessment.class));
        verifyNoInteractions(transactionManager);
    }

    @Test
    void createRejectsMissingApprovedThreshold() {
        when(thresholdService.getEffectiveThreshold(
                eq(100L), any(LocalDateTime.class)))
                .thenThrow(new InvalidRequestException(
                        "No approved threshold"));

        assertThrows(InvalidRequestException.class,
                () -> service.create(validRequest()));

        verify(assessmentRepository, never())
                .save(any(RiskAssessment.class));
        verifyNoInteractions(transactionManager);
    }

    @Test
    void invalidCalculationInputBecomesInvalidRequest() {
        prepareThreshold();

        CreateAssessmentRequest request = new CreateAssessmentRequest(
                100L,
                List.of(
                        snapshot(1L, 100L, 7, "0"),
                        snapshot(2L, 100L, 8, "80"),
                        snapshot(3L, 100L, 9, "60")
                )
        );

        // Oldest revenue cannot be zero because it is a denominator.
        assertThrows(InvalidRequestException.class,
                () -> service.create(request));

        verifyNoInteractions(transactionManager);
    }

    @Test
    void indicatorSaveFailureTriggersRollback() {
        prepareThreshold();
        prepareTransaction();

        when(assessmentRepository.save(any(RiskAssessment.class)))
                .thenReturn(1L);

        doThrow(new DataIntegrityViolationException(
                "Indicator persistence failed"))
                .when(indicatorRepository).saveAll(anyList());

        assertThrows(InvalidRequestException.class,
                () -> service.create(validRequest()));

        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void createFromFinanceLoadsSnapshotsAndReturnsExistingAssessment() {
        when(financeClient.getAssessmentSnapshots(
                100L, LocalDate.of(2026, 9, 1)))
                .thenReturn(validRequest().snapshots());

        when(assessmentRepository.findBySnapshotId(3L))
                .thenReturn(Optional.of(savedAssessment(100L)));
        when(assessmentRepository.findById(1L))
                .thenReturn(Optional.of(savedAssessment(100L)));

        AssessmentResponse result = service.createFromFinance(
                new AssessmentFromFinanceRequest(
                        100L, LocalDate.of(2026, 9, 1))
        );

        assertEquals(1L, result.assessmentId());

        verify(financeClient).getAssessmentSnapshots(
                100L, LocalDate.of(2026, 9, 1));
    }

    @Test
    void createFromFinanceRejectsInvalidReportingDate() {
        assertThrows(InvalidRequestException.class,
                () -> service.createFromFinance(
                        new AssessmentFromFinanceRequest(
                                100L, LocalDate.of(2026, 9, 15))));

        verifyNoInteractions(financeClient, assessmentRepository);
    }

    @Test
    void getByIdRejectsMissingAssessment() {
        when(assessmentRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getById(999L));

        verifyNoInteractions(accessClient, indicatorRepository);
    }

    @Test
    void historyRejectsInvalidPagination() {
        assertAll(
                () -> assertThrows(InvalidRequestException.class,
                        () -> service.getHistory(100L, -1, 10)),
                () -> assertThrows(InvalidRequestException.class,
                        () -> service.getHistory(100L, 0, 0)),
                () -> assertThrows(InvalidRequestException.class,
                        () -> service.getHistory(100L, 0, 101))
        );

        verifyNoInteractions(accessClient, assessmentRepository);
    }

    private void prepareThreshold() {
        ThresholdConfig approved = new ThresholdConfig(
                5L, 100L, 1,
                bd("15"), bd("30"), bd("70"),
                ThresholdStatus.APPROVED,
                10L, LocalDateTime.now().minusDays(2),
                20L, LocalDateTime.now().minusDays(1),
                "Approved",
                LocalDateTime.now().minusDays(1)
        );

        when(thresholdService.getEffectiveThreshold(
                eq(100L), any(LocalDateTime.class)))
                .thenReturn(approved);
    }

    private void prepareTransaction() {
        when(transactionManager.getTransaction(
                any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
    }

    private CreateAssessmentRequest validRequest() {
        return new CreateAssessmentRequest(
                100L,
                List.of(
                        snapshot(1L, 100L, 7, "100"),
                        snapshot(2L, 100L, 8, "80"),
                        snapshot(3L, 100L, 9, "60")
                )
        );
    }

    private FinancialSnapshotRequest snapshot(
            Long id, Long msmeId, int month, String revenue) {

        return new FinancialSnapshotRequest(
                id,
                msmeId,
                LocalDate.of(2026, month, 1),
                bd(revenue),
                bd("100"),
                bd("40"),
                bd("80"),
                bd("100")
        );
    }

    private RiskAssessment savedAssessment(Long msmeId) {
        return new RiskAssessment(
                1L,
                msmeId,
                3L,
                5L,
                LocalDateTime.now(),
                Severity.HIGH_PRIORITY,
                StressPattern.BROAD_FINANCIAL_DETERIORATION,
                null,
                LocalDateTime.now()
        );
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}