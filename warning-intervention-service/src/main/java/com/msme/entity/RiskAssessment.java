package com.msme.entity;

import java.time.*;
import java.math.BigDecimal;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "RISK_ASSESSMENT")
@Immutable
public class RiskAssessment {
    @Id
    @Column(name = "ASSESSMENT_ID")
    private Long assessmentId;

    @Column(name = "MSME_ID")
    private Long msmeId;

    @Column(name = "SNAPSHOT_ID")
    private Long snapshotId;

    @Column(name = "SEVERITY")
    private String severity;

    @Column(name = "STRESS_PATTERN")
    private String stressPattern;

    @Column(name = "ASSESSMENT_DATE")
    private LocalDateTime assessmentDate;

    public RiskAssessment() {}

    public Long getAssessmentId() { return assessmentId; }
    public void setAssessmentId(Long assessmentId) { this.assessmentId = assessmentId; }
    public Long getMsmeId() { return msmeId; }
    public void setMsmeId(Long msmeId) { this.msmeId = msmeId; }
    public Long getSnapshotId() { return snapshotId; }
    public void setSnapshotId(Long snapshotId) { this.snapshotId = snapshotId; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getStressPattern() { return stressPattern; }
    public void setStressPattern(String stressPattern) { this.stressPattern = stressPattern; }
    public LocalDateTime getAssessmentDate() { return assessmentDate; }
    public void setAssessmentDate(LocalDateTime assessmentDate) { this.assessmentDate = assessmentDate; }
}
