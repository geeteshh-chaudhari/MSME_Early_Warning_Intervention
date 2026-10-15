package com.msme.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(
    name = "FINANCIAL_SNAPSHOT",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "UQ_SNAPSHOT_MSME_PERIOD",
            columnNames = {
                "MSME_ID",
                "REPORTING_PERIOD"
            }
        )
    }
)
public class FinancialSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SNAPSHOT_ID")
    private Long snapshotId;

    @Column(name = "MSME_ID", nullable = false)
    private Long msmeId;

    @Column(name = "REPORTING_PERIOD", nullable = false)
    private LocalDate reportingPeriod;

    @Column(name = "REVENUE",
            nullable = false, precision = 18, scale = 2)
    private BigDecimal revenue;

    @Column(name = "TOTAL_RECEIVABLES",
            nullable = false, precision = 18, scale = 2)
    private BigDecimal totalReceivables;

    @Column(name = "OVERDUE_RECEIVABLES",
            nullable = false, precision = 18, scale = 2)
    private BigDecimal overdueReceivables;

    @Column(name = "CREDIT_USED",
            nullable = false, precision = 18, scale = 2)
    private BigDecimal creditUsed;

    @Column(name = "SUBMITTED_BY_USER_ID", nullable = false)
    private Long submittedByUserId;

    @Column(name = "SUBMITTED_AT", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    public FinancialSnapshot() {
    }
    public Long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId) {
        this.snapshotId = snapshotId;
    }

    public Long getMsmeId() {
        return msmeId;
    }

    public void setMsmeId(Long msmeId) {
        this.msmeId = msmeId;
    }

    public LocalDate getReportingPeriod() {
        return reportingPeriod;
    }

    public void setReportingPeriod(LocalDate reportingPeriod) {
        this.reportingPeriod = reportingPeriod;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }

    public void setRevenue(BigDecimal revenue) {
        this.revenue = revenue;
    }

    public BigDecimal getTotalReceivables() {
        return totalReceivables;
    }

    public void setTotalReceivables(BigDecimal totalReceivables) {
        this.totalReceivables = totalReceivables;
    }

    public BigDecimal getOverdueReceivables() {
        return overdueReceivables;
    }

    public void setOverdueReceivables(BigDecimal overdueReceivables) {
        this.overdueReceivables = overdueReceivables;
    }

    public BigDecimal getCreditUsed() {
        return creditUsed;
    }

    public void setCreditUsed(BigDecimal creditUsed) {
        this.creditUsed = creditUsed;
    }

    public Long getSubmittedByUserId() {
        return submittedByUserId;
    }

    public void setSubmittedByUserId(Long submittedByUserId) {
        this.submittedByUserId = submittedByUserId;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}