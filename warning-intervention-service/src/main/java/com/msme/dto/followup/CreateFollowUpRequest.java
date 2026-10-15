package com.msme.dto.followup;

import java.time.*;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public class CreateFollowUpRequest {
    @NotNull @Positive
    private Long assessmentId;

    @NotBlank @Pattern(regexp = "IMPROVING|STABILISING|STILL_STRESSED|ESCALATED")
    private String outcome;

    @NotBlank @Size(max = 4000)
    private String reviewNotes;

    @NotNull
    private Boolean keepInterventionActive;

    @FutureOrPresent
    private LocalDate nextFollowUpDate;

    public CreateFollowUpRequest() {}

    public CreateFollowUpRequest(Long assessmentId, String outcome, String reviewNotes, Boolean keepInterventionActive, LocalDate nextFollowUpDate) {
        this.assessmentId = assessmentId;
        this.outcome = outcome;
        this.reviewNotes = reviewNotes;
        this.keepInterventionActive = keepInterventionActive;
        this.nextFollowUpDate = nextFollowUpDate;
    }
    public Long getAssessmentId() { return assessmentId; }
    public void setAssessmentId(Long assessmentId) { this.assessmentId = assessmentId; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }
    public Boolean getKeepInterventionActive() { return keepInterventionActive; }
    public void setKeepInterventionActive(Boolean keepInterventionActive) { this.keepInterventionActive = keepInterventionActive; }
    public LocalDate getNextFollowUpDate() { return nextFollowUpDate; }
    public void setNextFollowUpDate(LocalDate nextFollowUpDate) { this.nextFollowUpDate = nextFollowUpDate; }
}
