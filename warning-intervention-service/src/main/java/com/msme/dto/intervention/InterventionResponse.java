package com.msme.dto.intervention;

import java.time.*;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public class InterventionResponse {
    
    private Long interventionId;

    
    private Long warningId;

    
    private Long createdByUserId;

    
    private String actionText;

    
    private String notes;

    
    private String isActive;

    
    private LocalDate followUpDate;

    
    private LocalDateTime createdAt;

    
    private LocalDateTime closedAt;

    
    private Long closedByUserId;

    public InterventionResponse() {}

    public InterventionResponse(Long interventionId, Long warningId, Long createdByUserId, String actionText, String notes, String isActive, LocalDate followUpDate, LocalDateTime createdAt, LocalDateTime closedAt, Long closedByUserId) {
        this.interventionId = interventionId;
        this.warningId = warningId;
        this.createdByUserId = createdByUserId;
        this.actionText = actionText;
        this.notes = notes;
        this.isActive = isActive;
        this.followUpDate = followUpDate;
        this.createdAt = createdAt;
        this.closedAt = closedAt;
        this.closedByUserId = closedByUserId;
    }
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
