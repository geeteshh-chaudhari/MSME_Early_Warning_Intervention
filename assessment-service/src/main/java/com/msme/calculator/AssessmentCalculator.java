
package com.msme.calculator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.msme.calculator.AssessmentCalculationResult.IndicatorCalculation;
import com.msme.enums.IndicatorCode;
import com.msme.enums.Severity;
import com.msme.enums.StressPattern;

@Component
public class AssessmentCalculator {

    private static final BigDecimal HUNDRED =
            new BigDecimal("100");

    private static final MathContext PRECISION =
            new MathContext(34, RoundingMode.HALF_UP);

    public record Snapshot(
            Long snapshotId,
            Long msmeId,
            LocalDate reportingPeriod,
            BigDecimal revenue,
            BigDecimal totalReceivables,
            BigDecimal overdueReceivables,
            BigDecimal creditUsed,
            BigDecimal sanctionedCreditLimit
    ) {}

    public record Thresholds(
            BigDecimal revenueDeclinePct,
            BigDecimal overdueReceivablePct,
            BigDecimal creditUtilisationPct
    ) {}

    public AssessmentCalculationResult calculate(
            List<Snapshot> input,
            Thresholds thresholds) {

        validateSnapshots(input);
        validateThresholds(thresholds);

        List<Snapshot> snapshots = input.stream()
                .sorted(Comparator.comparing(
                        Snapshot::reportingPeriod))
                .toList();

        Snapshot oldest = snapshots.get(0);
        Snapshot middle = snapshots.get(1);
        Snapshot current = snapshots.get(2);

        BigDecimal firstDecline = percentage(
                oldest.revenue().subtract(middle.revenue()),
                oldest.revenue()
        );

        BigDecimal secondDecline = percentage(
                middle.revenue().subtract(current.revenue()),
                middle.revenue()
        );

        boolean revenueBreached =
                firstDecline.compareTo(
                        thresholds.revenueDeclinePct()) >= 0
                &&
                secondDecline.compareTo(
                        thresholds.revenueDeclinePct()) >= 0;

        BigDecimal overduePct = percentage(
                current.overdueReceivables(),
                current.totalReceivables()
        );

        boolean overdueBreached =
                overduePct.compareTo(
                        thresholds.overdueReceivablePct()) > 0;

        BigDecimal creditPct = percentage(
                current.creditUsed(),
                current.sanctionedCreditLimit()
        );

        boolean creditBreached =
                creditPct.compareTo(
                        thresholds.creditUtilisationPct()) > 0;

        int count = 0;

        if (revenueBreached) count++;
        if (overdueBreached) count++;
        if (creditBreached) count++;

        Severity severity =
                Severity.fromBreachedCount(count);

        StressPattern pattern =
                StressPattern.determine(
                        revenueBreached,
                        overdueBreached,
                        creditBreached
                );

        List<IndicatorCalculation> indicators =
                new ArrayList<>();

        indicators.add(new IndicatorCalculation(
                IndicatorCode.REVENUE_DETERIORATION,
                secondDecline,
                thresholds.revenueDeclinePct(),
                revenueBreached,
                "Revenue declined by "
                    + display(firstDecline)
                    + "% in the first period and "
                    + display(secondDecline)
                    + "% in the second period. "
                    + "Both declines must be at least "
                    + display(thresholds.revenueDeclinePct())
                    + "% for a breach."
        ));

        indicators.add(new IndicatorCalculation(
                IndicatorCode.OVERDUE_RECEIVABLES,
                overduePct,
                thresholds.overdueReceivablePct(),
                overdueBreached,
                "Overdue receivables are "
                    + display(overduePct)
                    + "% of total receivables. "
                    + "A breach requires a value strictly above "
                    + display(thresholds.overdueReceivablePct())
                    + "%."
        ));

        indicators.add(new IndicatorCalculation(
                IndicatorCode.CREDIT_UTILISATION,
                creditPct,
                thresholds.creditUtilisationPct(),
                creditBreached,
                "Credit utilisation is "
                    + display(creditPct)
                    + "% of the sanctioned credit limit. "
                    + "A breach requires a value strictly above "
                    + display(thresholds.creditUtilisationPct())
                    + "%."
        ));

        return new AssessmentCalculationResult(
                severity,
                pattern,
                count,
                indicators
        );
    }

    private BigDecimal percentage(
            BigDecimal numerator,
            BigDecimal denominator) {

        if (denominator.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Percentage denominator must be positive");
        }

        return numerator.multiply(HUNDRED)
                .divide(denominator, PRECISION);
    }

    private String display(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private void validateSnapshots(List<Snapshot> snapshots) {

        if (snapshots == null || snapshots.size() != 3) {
            throw new IllegalArgumentException(
                    "Exactly three monthly snapshots are required");
        }

        Set<Long> ids = new HashSet<>();
        Long msmeId = null;

        for (Snapshot snapshot : snapshots) {

            if (snapshot == null
                    || snapshot.snapshotId() == null
                    || snapshot.msmeId() == null
                    || snapshot.reportingPeriod() == null) {
                throw new IllegalArgumentException(
                        "Snapshot ID, MSME ID and reporting period are required");
            }

            if (msmeId == null) {
                msmeId = snapshot.msmeId();
            } else if (!msmeId.equals(snapshot.msmeId())) {
                throw new IllegalArgumentException(
                        "All snapshots must belong to the same MSME");
            }

            if (!ids.add(snapshot.snapshotId())) {
                throw new IllegalArgumentException(
                        "Snapshot IDs must be distinct");
            }

            if (snapshot.reportingPeriod().getDayOfMonth() != 1) {
                throw new IllegalArgumentException(
                        "Reporting periods must be first-of-month dates");
            }

            requireNonNegative(snapshot.revenue(), "revenue");
            requireNonNegative(
                    snapshot.totalReceivables(), "totalReceivables");
            requireNonNegative(
                    snapshot.overdueReceivables(), "overdueReceivables");
            requireNonNegative(snapshot.creditUsed(), "creditUsed");

            requirePositive(
                    snapshot.sanctionedCreditLimit(),
                    "sanctionedCreditLimit");

            if (snapshot.overdueReceivables()
                    .compareTo(snapshot.totalReceivables()) > 0) {
                throw new IllegalArgumentException(
                        "Overdue receivables cannot exceed total receivables");
            }
        }

        List<Snapshot> ordered = snapshots.stream()
                .sorted(Comparator.comparing(
                        Snapshot::reportingPeriod))
                .toList();

        if (!ordered.get(0).reportingPeriod().plusMonths(1)
                    .equals(ordered.get(1).reportingPeriod())
                ||
            !ordered.get(1).reportingPeriod().plusMonths(1)
                    .equals(ordered.get(2).reportingPeriod())) {

            throw new IllegalArgumentException(
                    "Snapshots must cover three consecutive months");
        }

        requirePositive(ordered.get(0).revenue(),
                "oldest revenue");

        requirePositive(ordered.get(1).revenue(),
                "middle revenue");

        requirePositive(ordered.get(2).totalReceivables(),
                "current totalReceivables");
    }

    private void validateThresholds(Thresholds thresholds) {

        if (thresholds == null) {
            throw new IllegalArgumentException(
                    "Threshold configuration is required");
        }

        requirePercentage(
                thresholds.revenueDeclinePct(),
                "revenueDeclinePct");

        requirePercentage(
                thresholds.overdueReceivablePct(),
                "overdueReceivablePct");

        requireNonNegative(
                thresholds.creditUtilisationPct(),
                "creditUtilisationPct");
    }

    private void requirePercentage(
            BigDecimal value, String field) {

        requireNonNegative(value, field);

        if (value.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException(
                    field + " cannot exceed 100");
        }
    }

    private void requireNonNegative(
            BigDecimal value, String field) {

        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(
                    field + " must be nonnegative");
        }
    }

    private void requirePositive(
            BigDecimal value, String field) {

        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(
                    field + " must be greater than zero");
        }
    }
}
