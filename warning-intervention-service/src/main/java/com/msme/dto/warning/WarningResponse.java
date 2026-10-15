package com.msme.dto.warning;

import java.time.*;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public class WarningResponse {
    
    private Long warningId;

    
    private Long assessmentId;

    
    private String warningStatus;

    
    private String primaryDriver;

    
    private String whyNowText;

    
    private String recommendedAction;

    
    private LocalDateTime createdAt;

    
    private LocalDateTime closedAt;

    public WarningResponse() {}

    public WarningResponse(Long warningId, Long assessmentId, String warningStatus, String primaryDriver, String whyNowText, String recommendedAction, LocalDateTime createdAt, LocalDateTime closedAt) {
        this.warningId = warningId;
        this.assessmentId = assessmentId;
        this.warningStatus = warningStatus;
        this.primaryDriver = primaryDriver;
        this.whyNowText = whyNowText;
        this.recommendedAction = recommendedAction;
        this.createdAt = createdAt;
        this.closedAt = closedAt;
    }
    public Long getWarningId() { return warningId; }
    public void setWarningId(Long warningId) { this.warningId = warningId; }
    public Long getAssessmentId() { return assessmentId; }
    public void setAssessmentId(Long assessmentId) { this.assessmentId = assessmentId; }
    public String getWarningStatus() { return warningStatus; }
    public void setWarningStatus(String warningStatus) { this.warningStatus = warningStatus; }
    public String getPrimaryDriver() { return primaryDriver; }
    public void setPrimaryDriver(String primaryDriver) { this.primaryDriver = primaryDriver; }
    public String getWhyNowText() { return whyNowText; }
    public void setWhyNowText(String whyNowText) { this.whyNowText = whyNowText; }
    public String getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(String recommendedAction) { this.recommendedAction = recommendedAction; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
}
