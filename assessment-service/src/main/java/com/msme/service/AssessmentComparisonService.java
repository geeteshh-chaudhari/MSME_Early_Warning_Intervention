
package com.msme.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.msme.dto.assessment.AssessmentComparisonResponse;
import com.msme.dto.assessment.AssessmentResponse;
import com.msme.dto.assessment.IndicatorResultResponse;
import com.msme.enums.IndicatorCode;
import com.msme.exception.InvalidRequestException;

@Service
public class AssessmentComparisonService {

    private final AssessmentService assessmentService;

    public AssessmentComparisonService(
            AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    public AssessmentComparisonResponse compare(
            Long beforeId,
            Long afterId) {

        if (beforeId == null || afterId == null
                || beforeId <= 0 || afterId <= 0
                || beforeId.equals(afterId)) {
            throw new InvalidRequestException(
                    "Two distinct valid assessment IDs are required");
        }

        AssessmentResponse before =
                assessmentService.getById(beforeId);

        AssessmentResponse after =
                assessmentService.getById(afterId);

        if (!before.msmeId().equals(after.msmeId())) {
            throw new InvalidRequestException(
                    "Assessments must belong to the same MSME");
        }

        if (!before.assessmentDate()
                .isBefore(after.assessmentDate())) {
            throw new InvalidRequestException(
                    "Before assessment must be earlier "
                    + "than after assessment");
        }

        Map<IndicatorCode, IndicatorResultResponse> beforeMap =
                toMap(before.indicators());

        Map<IndicatorCode, IndicatorResultResponse> afterMap =
                toMap(after.indicators());

        List<AssessmentComparisonResponse.IndicatorComparison>
                comparisons = new ArrayList<>();

        boolean anyImproved = false;
        boolean anyStillBreached = false;

        for (IndicatorCode code : IndicatorCode.values()) {

            IndicatorResultResponse oldValue =
                    beforeMap.get(code);

            IndicatorResultResponse newValue =
                    afterMap.get(code);

            if (oldValue == null || newValue == null) {
                throw new InvalidRequestException(
                        "Both assessments must contain all "
                        + "three indicator results");
            }

            BigDecimal change = newValue.calculatedValue()
                    .subtract(oldValue.calculatedValue());

            // All three indicators represent risk percentages.
            // A lower value means an improvement.
            boolean improved = change.signum() < 0;

            if (improved) {
                anyImproved = true;
            }

            if (newValue.breached()) {
                anyStillBreached = true;
            }

            comparisons.add(
                new AssessmentComparisonResponse.IndicatorComparison(
                    code,
                    oldValue.calculatedValue(),
                    newValue.calculatedValue(),
                    oldValue.thresholdValueUsed(),
                    newValue.thresholdValueUsed(),
                    oldValue.breached(),
                    newValue.breached(),
                    change,
                    improved
                )
            );
        }

        int oldRank =
                before.severity().getBreachedCount();

        int newRank =
                after.severity().getBreachedCount();

        String outcome;

        if (newRank > oldRank) {
            outcome = "ESCALATED";

        } else if (newRank < oldRank && anyImproved) {
            outcome = "IMPROVING";

        } else if (anyStillBreached) {
            outcome = "STILL_STRESSED";

        } else {
            outcome = "STABILISING";
        }

        String explanation = buildExplanation(
                before,
                after,
                outcome
        );

        return new AssessmentComparisonResponse(
                before.msmeId(),
                before.assessmentId(),
                after.assessmentId(),
                before.assessmentDate(),
                after.assessmentDate(),
                before.severity(),
                after.severity(),
                outcome,
                comparisons,
                explanation
        );
    }

    private Map<IndicatorCode, IndicatorResultResponse> toMap(
            List<IndicatorResultResponse> indicators) {

        Map<IndicatorCode, IndicatorResultResponse> map =
                new EnumMap<>(IndicatorCode.class);

        if (indicators != null) {
            for (IndicatorResultResponse indicator : indicators) {
                map.put(indicator.indicatorCode(), indicator);
            }
        }

        return map;
    }

    private String buildExplanation(
            AssessmentResponse before,
            AssessmentResponse after,
            String outcome) {

        return "Observed assessment severity changed from "
                + before.severity()
                + " to "
                + after.severity()
                + ". Comparison outcome: "
                + outcome
                + ". This comparison describes observed "
                + "financial indicator changes only. "
                + "It does not establish that an "
                + "intervention caused the changes.";
    }
}
