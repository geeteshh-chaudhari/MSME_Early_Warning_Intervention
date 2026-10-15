
package com.msme.dto.assessment;

import java.math.BigDecimal;

import com.msme.enums.IndicatorCode;

public record IndicatorResultResponse(

        Long indicatorResultId,

        IndicatorCode indicatorCode,

        BigDecimal calculatedValue,

        BigDecimal thresholdValueUsed,

        boolean breached,

        String explanation

) {}
