
package com.msme.dto.assessment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AssessmentFromFinanceRequest(

        @NotNull
        @Positive
        Long msmeId

) {}
