package com.msme;

import com.msme.dto.assessment.AssessmentComparisonResponse;
import com.msme.dto.assessment.AssessmentResponse;
import com.msme.dto.assessment.IndicatorResultResponse;
import com.msme.enums.IndicatorCode;
import com.msme.enums.Severity;
import com.msme.exception.InvalidRequestException;
import com.msme.exception.ResourceNotFoundException;
import com.msme.service.AssessmentComparisonService;
import com.msme.service.AssessmentService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssessmentComparisonServiceTest {

    private static final LocalDateTime BEFORE =
            LocalDateTime.of(2026, 8, 1, 10, 0);

    private static final LocalDateTime AFTER =
            LocalDateTime.of(2026, 9, 1, 10, 0);

    @Mock
    private AssessmentService assessmentService;

    @InjectMocks
    private AssessmentComparisonService service;

    @Test
    void lowerSeverityAndRiskProducesImproving() {
        prepare(
                assessment(1L, 100L, BEFORE, "30", "40", "80"),
                assessment(2L, 100L, AFTER, "10", "20", "75")
        );

        AssessmentComparisonResponse result = service.compare(1L, 2L);

        assertEquals("IMPROVING", result.outcome());
        assertEquals(Severity.HIGH_PRIORITY, result.beforeSeverity());
        assertEquals(Severity.WATCH, result.afterSeverity());
        assertTrue(result.indicators().stream()
                .allMatch(item -> item.improved()));
    }

    @Test
    void higherSeverityProducesEscalated() {
        prepare(
                assessment(1L, 100L, BEFORE, "10", "20", "60"),
                assessment(2L, 100L, AFTER, "30", "40", "80")
        );

        AssessmentComparisonResponse result = service.compare(1L, 2L);

        assertEquals("ESCALATED", result.outcome());
        assertEquals(Severity.HEALTHY, result.beforeSeverity());
        assertEquals(Severity.HIGH_PRIORITY, result.afterSeverity());
    }

    @Test
    void continuingBreachProducesStillStressed() {
        prepare(
                assessment(1L, 100L, BEFORE, "10", "20", "80"),
                assessment(2L, 100L, AFTER, "10", "20", "85")
        );

        AssessmentComparisonResponse result = service.compare(1L, 2L);

        assertEquals("STILL_STRESSED", result.outcome());
        assertEquals(Severity.WATCH, result.afterSeverity());
    }

    @Test
    void unchangedHealthyAssessmentProducesStabilising() {
        prepare(
                assessment(1L, 100L, BEFORE, "10", "20", "60"),
                assessment(2L, 100L, AFTER, "10", "20", "60")
        );

        AssessmentComparisonResponse result = service.compare(1L, 2L);

        assertEquals("STABILISING", result.outcome());
        assertTrue(result.indicators().stream()
                .noneMatch(item -> item.improved()));
    }

    @Test
    void calculatesChangesAndRetainsAssessmentDetails() {
        prepare(
                assessment(1L, 100L, BEFORE, "30", "40", "80"),
                assessment(2L, 100L, AFTER, "20", "45", "80")
        );

        AssessmentComparisonResponse result = service.compare(1L, 2L);

        assertEquals(100L, result.msmeId());
        assertEquals(1L, result.beforeAssessmentId());
        assertEquals(2L, result.afterAssessmentId());
        assertEquals(3, result.indicators().size());

        var revenue = result.indicators().stream()
                .filter(item -> item.indicatorCode()
                        == IndicatorCode.REVENUE_DETERIORATION)
                .findFirst().orElseThrow();

        var overdue = result.indicators().stream()
                .filter(item -> item.indicatorCode()
                        == IndicatorCode.OVERDUE_RECEIVABLES)
                .findFirst().orElseThrow();

        var credit = result.indicators().stream()
                .filter(item -> item.indicatorCode()
                        == IndicatorCode.CREDIT_UTILISATION)
                .findFirst().orElseThrow();

        assertEquals(0, bd("-10").compareTo(revenue.change()));
        assertTrue(revenue.improved());

        assertEquals(0, bd("5").compareTo(overdue.change()));
        assertFalse(overdue.improved());

        assertEquals(0, bd("0").compareTo(credit.change()));
        assertFalse(credit.improved());

        assertNotNull(result.explanation());
        assertFalse(result.explanation().isBlank());
    }

    @Test
    void rejectsInvalidOrIdenticalAssessmentIds() {
        assertAll(
                () -> assertThrows(InvalidRequestException.class,
                        () -> service.compare(null, 2L)),
                () -> assertThrows(InvalidRequestException.class,
                        () -> service.compare(1L, null)),
                () -> assertThrows(InvalidRequestException.class,
                        () -> service.compare(0L, 2L)),
                () -> assertThrows(InvalidRequestException.class,
                        () -> service.compare(1L, -1L)),
                () -> assertThrows(InvalidRequestException.class,
                        () -> service.compare(1L, 1L))
        );

        verifyNoInteractions(assessmentService);
    }

    @Test
    void rejectsAssessmentsFromDifferentMsmes() {
        prepare(
                assessment(1L, 100L, BEFORE, "10", "20", "60"),
                assessment(2L, 999L, AFTER, "10", "20", "60")
        );

        assertThrows(InvalidRequestException.class,
                () -> service.compare(1L, 2L));
    }

    @Test
    void rejectsIncorrectChronologicalOrder() {
        prepare(
                assessment(1L, 100L, AFTER, "10", "20", "60"),
                assessment(2L, 100L, BEFORE, "10", "20", "60")
        );

        assertThrows(InvalidRequestException.class,
                () -> service.compare(1L, 2L));
    }

    @Test
    void rejectsMissingIndicatorResults() {
        AssessmentResponse complete =
                assessment(2L, 100L, AFTER, "10", "20", "60");

        AssessmentResponse incomplete = new AssessmentResponse(
                2L,
                100L,
                2L,
                5L,
                AFTER,
                Severity.HEALTHY,
                null,
                null,
                complete.indicators().subList(0, 2)
        );

        prepare(
                assessment(1L, 100L, BEFORE, "10", "20", "60"),
                incomplete
        );

        assertThrows(InvalidRequestException.class,
                () -> service.compare(1L, 2L));
    }

    @Test
    void propagatesMissingAssessmentError() {
        when(assessmentService.getById(1L))
                .thenThrow(new ResourceNotFoundException(
                        "Assessment", 1L));

        assertThrows(ResourceNotFoundException.class,
                () -> service.compare(1L, 2L));

        verify(assessmentService, never()).getById(2L);
    }

    private void prepare(
            AssessmentResponse before,
            AssessmentResponse after) {

        when(assessmentService.getById(1L)).thenReturn(before);
        when(assessmentService.getById(2L)).thenReturn(after);
    }

    private AssessmentResponse assessment(
            Long id,
            Long msmeId,
            LocalDateTime date,
            String revenue,
            String overdue,
            String credit) {

        // These are prepared assessment results, not raw financial data.
        boolean revenueBreached = bd(revenue).compareTo(bd("15")) >= 0;
        boolean overdueBreached = bd(overdue).compareTo(bd("30")) > 0;
        boolean creditBreached = bd(credit).compareTo(bd("70")) > 0;

        int breachedCount = (revenueBreached ? 1 : 0)
                + (overdueBreached ? 1 : 0)
                + (creditBreached ? 1 : 0);

        return new AssessmentResponse(
                id,
                msmeId,
                id,
                5L,
                date,
                Severity.fromBreachedCount(breachedCount),
                null,
                null,
                List.of(
                        indicator(IndicatorCode.REVENUE_DETERIORATION,
                                revenue, "15", revenueBreached),
                        indicator(IndicatorCode.OVERDUE_RECEIVABLES,
                                overdue, "30", overdueBreached),
                        indicator(IndicatorCode.CREDIT_UTILISATION,
                                credit, "70", creditBreached)
                )
        );
    }

    private IndicatorResultResponse indicator(
            IndicatorCode code,
            String value,
            String threshold,
            boolean breached) {

        return new IndicatorResultResponse(
                null,
                code,
                bd(value),
                bd(threshold),
                breached,
                "Test indicator"
        );
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}