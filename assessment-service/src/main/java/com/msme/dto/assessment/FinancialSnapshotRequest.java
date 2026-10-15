
package com.msme.dto.assessment;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record FinancialSnapshotRequest(

        @NotNull
        @Positive
        Long snapshotId,

        @NotNull
        @Positive
        Long msmeId,

        @NotNull
        LocalDate reportingPeriod,

        @NotNull
        @PositiveOrZero
        BigDecimal revenue,

        @NotNull
        @PositiveOrZero
        BigDecimal totalReceivables,

        @NotNull
        @PositiveOrZero
        BigDecimal overdueReceivables,

        @NotNull
        @PositiveOrZero
        BigDecimal creditUsed,

        @NotNull
        @Positive
        BigDecimal sanctionedCreditLimit

) {}
