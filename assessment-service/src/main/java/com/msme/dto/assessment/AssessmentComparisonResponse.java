
package com.msme.dto.assessment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.msme.enums.IndicatorCode;
import com.msme.enums.Severity;

public record AssessmentComparisonResponse(

        Long msmeId,

        Long beforeAssessmentId,

        Long afterAssessmentId,

        LocalDateTime beforeAssessmentDate,

        LocalDateTime afterAssessmentDate,

        Severity beforeSeverity,

        Severity afterSeverity,

        String outcome,

        List<IndicatorComparison> indicators,

        String explanation

) {

    public AssessmentComparisonResponse {
        if (indicators != null) {
            indicators = List.copyOf(indicators);
        }
    }

    public record IndicatorComparison(

            IndicatorCode indicatorCode,

            BigDecimal beforeValue,

            BigDecimal afterValue,

            BigDecimal beforeThreshold,

            BigDecimal afterThreshold,

            boolean beforeBreached,

            boolean afterBreached,

            BigDecimal change,

            boolean improved

    ) {}
}
