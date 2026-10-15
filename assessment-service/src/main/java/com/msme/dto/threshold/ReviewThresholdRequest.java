
package com.msme.dto.threshold;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.msme.enums.ThresholdStatus;

public record ReviewThresholdRequest(

        @NotNull(message = "Review decision is required")
        ThresholdStatus decision,

        @NotBlank(message = "Review reason is required")
        @Size(max = 2000, message = "Review reason is too long")
        String reason

) {

    public ReviewThresholdRequest {

        if (decision != null
                && decision != ThresholdStatus.APPROVED
                && decision != ThresholdStatus.REJECTED) {

            throw new IllegalArgumentException(
                "Review decision must be APPROVED or REJECTED"
            );
        }
    }
}
