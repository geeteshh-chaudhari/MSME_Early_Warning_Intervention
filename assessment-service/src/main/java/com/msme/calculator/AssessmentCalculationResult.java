
package com.msme.calculator;

import java.math.BigDecimal;
import java.util.List;

import com.msme.enums.IndicatorCode;
import com.msme.enums.Severity;
import com.msme.enums.StressPattern;

public record AssessmentCalculationResult(
        Severity severity,
        StressPattern stressPattern,
        int breachedIndicatorCount,
        List<IndicatorCalculation> indicators
) {

    public AssessmentCalculationResult {
        indicators = List.copyOf(indicators);
    }

    public record IndicatorCalculation(
            IndicatorCode indicatorCode,
            BigDecimal calculatedValue,
            BigDecimal thresholdValue,
            boolean breached,
            String explanation
    ) {}
}
