package com.msme.entity;

import java.time.*;
import java.math.BigDecimal;
import jakarta.persistence.*;

@Entity
@Table(name = "WARNING", uniqueConstraints = @UniqueConstraint(columnNames = "ASSESSMENT_ID"))
public class Warning {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "WARNING_ID")
    private Long warningId;

    @Column(name = "ASSESSMENT_ID")
    private Long assessmentId;

    @Column(name = "WARNING_STATUS")
    private String warningStatus;

    @Column(name = "PRIMARY_DRIVER")
    private String primaryDriver;

    @Lob
    @Column(name = "WHY_NOW_TEXT")
    private String whyNowText;

    @Lob
    @Column(name = "RECOMMENDED_ACTION")
    private String recommendedAction;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "CLOSED_AT")
    private LocalDateTime closedAt;

    public Warning() {}

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
