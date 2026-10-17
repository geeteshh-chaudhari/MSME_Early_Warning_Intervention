package com.msme.dto.assessment;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AssessmentFromFinanceRequest(

        @NotNull
        @Positive
        Long msmeId,

        @NotNull
        LocalDate reportingPeriod

) {

    @JsonIgnore
    @AssertTrue(
            message = "reportingPeriod must be the first day of a month"
    )
    public boolean isFirstDayOfMonth() {
        return reportingPeriod == null
                || reportingPeriod.getDayOfMonth() == 1;
    }
}