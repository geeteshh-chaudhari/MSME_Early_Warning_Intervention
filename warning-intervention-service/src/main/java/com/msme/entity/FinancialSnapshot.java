package com.msme.entity;

import java.time.*;
import java.math.BigDecimal;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "FINANCIAL_SNAPSHOT")
@Immutable
public class FinancialSnapshot {
    @Id
    @Column(name = "SNAPSHOT_ID")
    private Long snapshotId;

    @Column(name = "MSME_ID")
    private Long msmeId;

    @Column(name = "REPORTING_PERIOD")
    private LocalDate reportingPeriod;

    public FinancialSnapshot() {}

    public Long getSnapshotId() { return snapshotId; }
    public void setSnapshotId(Long snapshotId) { this.snapshotId = snapshotId; }
    public Long getMsmeId() { return msmeId; }
    public void setMsmeId(Long msmeId) { this.msmeId = msmeId; }
    public LocalDate getReportingPeriod() { return reportingPeriod; }
    public void setReportingPeriod(LocalDate reportingPeriod) { this.reportingPeriod = reportingPeriod; }
}
