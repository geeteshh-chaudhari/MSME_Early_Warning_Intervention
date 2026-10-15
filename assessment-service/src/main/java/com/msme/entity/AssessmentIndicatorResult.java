
package com.msme.entity;

import java.math.BigDecimal;

import com.msme.enums.IndicatorCode;

public record AssessmentIndicatorResult(

        Long indicatorResultId,

        Long assessmentId,

        IndicatorCode indicatorCode,

        BigDecimal calculatedValue,

        BigDecimal thresholdValueUsed,

        boolean breached,

        String explanationText

) {

    public String getBreachedFlag() {
        return breached ? "Y" : "N";
    }

    public static boolean fromBreachedFlag(String flag) {
        return "Y".equals(flag);
    }
}
