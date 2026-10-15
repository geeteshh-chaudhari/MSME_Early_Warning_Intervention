package com.msme.dto.warning;

import java.time.*;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public class CreateWarningRequest {
    @NotNull @Positive
    private Long assessmentId;

    public CreateWarningRequest() {}

    public CreateWarningRequest(Long assessmentId) {
        this.assessmentId = assessmentId;
    }
    public Long getAssessmentId() { return assessmentId; }
    public void setAssessmentId(Long assessmentId) { this.assessmentId = assessmentId; }
}
