
package com.msme.dto.threshold;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateThresholdRequest(

        @NotNull(message = "MSME ID is required")
        @Positive(message = "MSME ID must be positive")
        Long msmeId,

        @NotNull(message = "Revenue decline threshold is required")
        @DecimalMin(value = "0.00")
        @DecimalMax(value = "100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal revenueDeclinePct,

        @NotNull(message = "Overdue receivable threshold is required")
        @DecimalMin(value = "0.00")
        @DecimalMax(value = "100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal overdueReceivablePct,

        @NotNull(message = "Credit utilisation threshold is required")
        @DecimalMin(value = "0.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal creditUtilisationPct,

        LocalDateTime effectiveFrom

) {}
