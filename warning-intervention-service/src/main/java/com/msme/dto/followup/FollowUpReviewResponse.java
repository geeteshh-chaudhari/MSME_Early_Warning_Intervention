package com.msme.dto.followup;

import java.time.*;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public class FollowUpReviewResponse {
    
    private Long followupId;

    
    private Long interventionId;

    
    private Long assessmentId;

    
    private Long reviewedByUserId;

    
    private String outcome;

    
    private String reviewNotes;

    
    private LocalDateTime reviewedAt;

    
    private String keepInterventionActive;

    public FollowUpReviewResponse() {}

    public FollowUpReviewResponse(Long followupId, Long interventionId, Long assessmentId, Long reviewedByUserId, String outcome, String reviewNotes, LocalDateTime reviewedAt, String keepInterventionActive) {
        this.followupId = followupId;
        this.interventionId = interventionId;
        this.assessmentId = assessmentId;
        this.reviewedByUserId = reviewedByUserId;
        this.outcome = outcome;
        this.reviewNotes = reviewNotes;
        this.reviewedAt = reviewedAt;
        this.keepInterventionActive = keepInterventionActive;
    }
    public Long getFollowupId() { return followupId; }
    public void setFollowupId(Long followupId) { this.followupId = followupId; }
    public Long getInterventionId() { return interventionId; }
    public void setInterventionId(Long interventionId) { this.interventionId = interventionId; }
    public Long getAssessmentId() { return assessmentId; }
    public void setAssessmentId(Long assessmentId) { this.assessmentId = assessmentId; }
    public Long getReviewedByUserId() { return reviewedByUserId; }
    public void setReviewedByUserId(Long reviewedByUserId) { this.reviewedByUserId = reviewedByUserId; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getKeepInterventionActive() { return keepInterventionActive; }
    public void setKeepInterventionActive(String keepInterventionActive) { this.keepInterventionActive = keepInterventionActive; }
}
