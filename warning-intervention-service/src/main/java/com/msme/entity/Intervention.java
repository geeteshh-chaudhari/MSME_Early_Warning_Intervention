package com.msme.entity;

import java.time.*;
import java.math.BigDecimal;
import jakarta.persistence.*;

@Entity
@Table(name = "INTERVENTION")
public class Intervention {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "INTERVENTION_ID")
    private Long interventionId;

    @Column(name = "WARNING_ID")
    private Long warningId;

    @Column(name = "CREATED_BY_USER_ID")
    private Long createdByUserId;

    @Lob
    @Column(name = "ACTION_TEXT")
    private String actionText;

    @Lob
    @Column(name = "NOTES")
    private String notes;

    @Column(name = "IS_ACTIVE")
    private String isActive;

    @Column(name = "FOLLOW_UP_DATE")
    private LocalDate followUpDate;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "CLOSED_AT")
    private LocalDateTime closedAt;

    @Column(name = "CLOSED_BY_USER_ID")
    private Long closedByUserId;

    public Intervention() {}

    public Long getInterventionId() { return interventionId; }
    public void setInterventionId(Long interventionId) { this.interventionId = interventionId; }
    public Long getWarningId() { return warningId; }
    public void setWarningId(Long warningId) { this.warningId = warningId; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Long createdByUserId) { this.createdByUserId = createdByUserId; }
    public String getActionText() { return actionText; }
    public void setActionText(String actionText) { this.actionText = actionText; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getIsActive() { return isActive; }
    public void setIsActive(String isActive) { this.isActive = isActive; }
    public LocalDate getFollowUpDate() { return followUpDate; }
    public void setFollowUpDate(LocalDate followUpDate) { this.followUpDate = followUpDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public Long getClosedByUserId() { return closedByUserId; }
    public void setClosedByUserId(Long closedByUserId) { this.closedByUserId = closedByUserId; }
}
