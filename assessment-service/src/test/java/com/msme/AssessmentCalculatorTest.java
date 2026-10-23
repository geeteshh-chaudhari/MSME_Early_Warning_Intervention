package com.msme;

import com.msme.calculator.AssessmentCalculator;
import com.msme.calculator.AssessmentCalculator.Snapshot;
import com.msme.calculator.AssessmentCalculator.Thresholds;
import com.msme.calculator.AssessmentCalculationResult;
import com.msme.calculator.AssessmentCalculationResult.IndicatorCalculation;
import com.msme.enums.IndicatorCode;
import com.msme.enums.Severity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AssessmentCalculatorTest {

    private final AssessmentCalculator calculator =
            new AssessmentCalculator();

    private final Thresholds thresholds = new Thresholds(
            bd("15"), bd("30"), bd("70")
    );

    @Test
    void noBreachesProducesHealthyAssessment() {
        AssessmentCalculationResult result =
                calculate("100", "100", "100", "20", "60");

        assertEquals(Severity.HEALTHY, result.severity());
        assertEquals(0, result.breachedIndicatorCount());
        assertEquals(3, result.indicators().size());
        assertTrue(result.indicators().stream()
                .noneMatch(IndicatorCalculation::breached));
    }

    @Test
    void oneBreachProducesWatchAssessment() {
        AssessmentCalculationResult result =
                calculate("100", "100", "100", "40", "60");

        assertEquals(Severity.WATCH, result.severity());
        assertEquals(1, result.breachedIndicatorCount());
        assertTrue(indicator(result,
                IndicatorCode.OVERDUE_RECEIVABLES).breached());
    }

    @Test
    void twoBreachesProduceEarlyStressAssessment() {
        AssessmentCalculationResult result =
                calculate("100", "100", "100", "40", "80");

        assertEquals(Severity.EARLY_STRESS, result.severity());
        assertEquals(2, result.breachedIndicatorCount());
    }

    @Test
    void threeBreachesProduceHighPriorityAssessment() {
        AssessmentCalculationResult result =
                calculate("100", "80", "60", "40", "80");

        assertEquals(Severity.HIGH_PRIORITY, result.severity());
        assertEquals(3, result.breachedIndicatorCount());
        assertTrue(result.indicators().stream()
                .allMatch(IndicatorCalculation::breached));
    }

    @Test
    void revenueBreachRequiresDeclineInBothMonths() {
        // First decline is only 10%; second decline is over 15%.
        AssessmentCalculationResult result =
                calculate("100", "90", "60", "20", "60");

        assertFalse(indicator(result,
                IndicatorCode.REVENUE_DETERIORATION).breached());
        assertEquals(Severity.HEALTHY, result.severity());
    }

    @Test
    void revenueExactlyAtThresholdCountsAsBreach() {
        // 100 -> 85 -> 72.25: exactly 15% decline each month.
        AssessmentCalculationResult result =
                calculate("100", "85", "72.25", "20", "60");

        IndicatorCalculation revenue = indicator(result,
                IndicatorCode.REVENUE_DETERIORATION);

        assertTrue(revenue.breached());
        assertDecimal("15", revenue.calculatedValue());
        assertEquals(Severity.WATCH, result.severity());
    }

    @Test
    void overdueAndCreditExactlyAtThresholdDoNotBreach() {
        AssessmentCalculationResult result =
                calculate("100", "100", "100", "30", "70");

        assertFalse(indicator(result,
                IndicatorCode.OVERDUE_RECEIVABLES).breached());
        assertFalse(indicator(result,
                IndicatorCode.CREDIT_UTILISATION).breached());
        assertEquals(Severity.HEALTHY, result.severity());
    }

    @Test
    void calculatesFinancialPercentagesCorrectly() {
        AssessmentCalculationResult result =
                calculate("100", "80", "60", "40", "80");

        // Second revenue decline: (80 - 60) / 80 * 100 = 25%.
        assertDecimal("25", indicator(result,
                IndicatorCode.REVENUE_DETERIORATION).calculatedValue());

        // Total receivables and credit limit are both 100.
        assertDecimal("40", indicator(result,
                IndicatorCode.OVERDUE_RECEIVABLES).calculatedValue());

        assertDecimal("80", indicator(result,
                IndicatorCode.CREDIT_UTILISATION).calculatedValue());
    }

    @Test
    void acceptsSnapshotsInAnyOrder() {
        List<Snapshot> ordered =
                snapshots("100", "80", "60", "40", "80");

        AssessmentCalculationResult expected =
                calculator.calculate(ordered, thresholds);

        AssessmentCalculationResult actual = calculator.calculate(
                List.of(ordered.get(2), ordered.get(0), ordered.get(1)),
                thresholds
        );

        assertEquals(expected, actual);
    }

    @Test
    void rejectsFewerThanThreeSnapshots() {
        List<Snapshot> input =
                snapshots("100", "100", "100", "20", "60");

        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(input.subList(0, 2), thresholds));
    }

    @Test
    void rejectsSnapshotsFromDifferentMsmes() {
        List<Snapshot> input =
                snapshots("100", "100", "100", "20", "60");

        Snapshot otherMsme = snapshot(
                3L, 999L, LocalDate.of(2026, 9, 1),
                "100", "100", "20", "60", "100"
        );

        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(
                        List.of(input.get(0), input.get(1), otherMsme),
                        thresholds));
    }

    @Test
    void rejectsNonConsecutiveMonths() {
        List<Snapshot> input =
                snapshots("100", "100", "100", "20", "60");

        Snapshot missingSeptember = snapshot(
                3L, 100L, LocalDate.of(2026, 10, 1),
                "100", "100", "20", "60", "100"
        );

        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(
                        List.of(input.get(0), input.get(1), missingSeptember),
                        thresholds));
    }

    @Test
    void acceptsZeroHistoricalRevenueWithoutInventingPercentage() {
        assertEquals(Severity.HEALTHY, calculate("0", "100", "100", "20", "60").severity());
    }

    @Test
    void acceptsZeroCurrentTotalReceivables() {
        List<Snapshot> input =
                snapshots("100", "100", "100", "20", "60");

        Snapshot invalidCurrent = snapshot(
                3L, 100L, LocalDate.of(2026, 9, 1),
                "100", "0", "0", "60", "100"
        );

        assertEquals(Severity.HEALTHY, calculator.calculate(
                List.of(input.get(0), input.get(1), invalidCurrent), thresholds).severity());
    }

    @Test
    void rejectsOverdueAboveTotalReceivables() {
        assertThrows(IllegalArgumentException.class,
                () -> calculate("100", "100", "100", "101", "60"));
    }

    @Test
    void rejectsNegativeFinancialValues() {
        assertThrows(IllegalArgumentException.class,
                () -> calculate("100", "100", "100", "20", "-1"));
    }

    @Test
    void rejectsInvalidThresholdPercentage() {
        Thresholds invalidThresholds = new Thresholds(
                bd("101"), bd("30"), bd("70")
        );

        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(
                        snapshots("100", "100", "100", "20", "60"),
                        invalidThresholds));
    }

    private AssessmentCalculationResult calculate(
            String oldestRevenue,
            String middleRevenue,
            String currentRevenue,
            String overdue,
            String creditUsed) {

        return calculator.calculate(
                snapshots(oldestRevenue, middleRevenue,
                        currentRevenue, overdue, creditUsed),
                thresholds
        );
    }

    private List<Snapshot> snapshots(
            String oldestRevenue,
            String middleRevenue,
            String currentRevenue,
            String overdue,
            String creditUsed) {

        return List.of(
                snapshot(1L, 100L, LocalDate.of(2026, 7, 1),
                        oldestRevenue, "100", "20", "60", "100"),

                snapshot(2L, 100L, LocalDate.of(2026, 8, 1),
                        middleRevenue, "100", "20", "60", "100"),

                snapshot(3L, 100L, LocalDate.of(2026, 9, 1),
                        currentRevenue, "100", overdue, creditUsed, "100")
        );
    }

    private Snapshot snapshot(
            Long id,
            Long msmeId,
            LocalDate period,
            String revenue,
            String totalReceivables,
            String overdue,
            String creditUsed,
            String creditLimit) {

        return new Snapshot(
                id, msmeId, period,
                bd(revenue),
                bd(totalReceivables),
                bd(overdue),
                bd(creditUsed),
                bd(creditLimit)
        );
    }

    private IndicatorCalculation indicator(
            AssessmentCalculationResult result,
            IndicatorCode code) {

        return result.indicators().stream()
                .filter(item -> item.indicatorCode() == code)
                .findFirst()
                .orElseThrow();
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, bd(expected).compareTo(actual),
                "Expected " + expected + " but got " + actual);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}