package com.msme.entity;

import java.time.*;
import java.math.BigDecimal;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "ASSESSMENT_INDICATOR_RESULT")
@Immutable
public class AssessmentIndicatorResult {
    @Id
    @Column(name = "INDICATOR_RESULT_ID")
    private Long indicatorResultId;

    @Column(name = "ASSESSMENT_ID")
    private Long assessmentId;

    @Column(name = "INDICATOR_CODE")
    private String indicatorCode;

    @Column(name = "CALCULATED_VALUE")
    private BigDecimal calculatedValue;

    @Column(name = "THRESHOLD_VALUE_USED")
    private BigDecimal thresholdValueUsed;

    @Column(name = "BREACHED_FLAG")
    private String breachedFlag;

    @Lob
    @Column(name = "EXPLANATION_TEXT")
    private String explanationText;

    public AssessmentIndicatorResult() {}

    public Long getIndicatorResultId() { return indicatorResultId; }
    public void setIndicatorResultId(Long indicatorResultId) { this.indicatorResultId = indicatorResultId; }
    public Long getAssessmentId() { return assessmentId; }
    public void setAssessmentId(Long assessmentId) { this.assessmentId = assessmentId; }
    public String getIndicatorCode() { return indicatorCode; }
    public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }
    public BigDecimal getCalculatedValue() { return calculatedValue; }
    public void setCalculatedValue(BigDecimal calculatedValue) { this.calculatedValue = calculatedValue; }
    public BigDecimal getThresholdValueUsed() { return thresholdValueUsed; }
    public void setThresholdValueUsed(BigDecimal thresholdValueUsed) { this.thresholdValueUsed = thresholdValueUsed; }
    public String getBreachedFlag() { return breachedFlag; }
    public void setBreachedFlag(String breachedFlag) { this.breachedFlag = breachedFlag; }
    public String getExplanationText() { return explanationText; }
    public void setExplanationText(String explanationText) { this.explanationText = explanationText; }
}
