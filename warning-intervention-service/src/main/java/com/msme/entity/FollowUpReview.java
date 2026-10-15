package com.msme.entity;

import java.time.*;
import java.math.BigDecimal;
import jakarta.persistence.*;

@Entity
@Table(name = "FOLLOW_UP_REVIEW", uniqueConstraints = @UniqueConstraint(columnNames = {"INTERVENTION_ID", "ASSESSMENT_ID"}))
public class FollowUpReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FOLLOWUP_ID")
    private Long followupId;

    @Column(name = "INTERVENTION_ID")
    private Long interventionId;

    @Column(name = "ASSESSMENT_ID")
    private Long assessmentId;

    @Column(name = "REVIEWED_BY_USER_ID")
    private Long reviewedByUserId;

    @Column(name = "OUTCOME")
    private String outcome;

    @Lob
    @Column(name = "REVIEW_NOTES")
    private String reviewNotes;

    @Column(name = "REVIEWED_AT")
    private LocalDateTime reviewedAt;

    @Column(name = "KEEP_INTERVENTION_ACTIVE")
    private String keepInterventionActive;

    public FollowUpReview() {}

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
