package com.msme.dto.assessment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AssessmentInputResponse(

        @NotNull
        @Positive
        Long msmeId,

        String businessName,

        @NotNull
        @Positive
        BigDecimal sanctionedCreditLimit,

        @NotNull
        LocalDate reportingPeriod,

        @NotNull
        @Size(min = 3, max = 3)
        List<@NotNull @Valid SnapshotResponse> snapshots

) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SnapshotResponse(

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
            BigDecimal creditUsed

    ) {}
}