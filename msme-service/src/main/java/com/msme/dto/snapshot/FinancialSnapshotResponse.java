package com.msme.dto.snapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class FinancialSnapshotResponse {

    private Long snapshotId;

    private Long msmeId;

    private LocalDate reportingPeriod;

    private BigDecimal revenue;

    private BigDecimal totalReceivables;

    private BigDecimal overdueReceivables;

    private BigDecimal creditUsed;

    private Long submittedByUserId;

    private LocalDateTime createdAt;

    public FinancialSnapshotResponse() {
    }

    public FinancialSnapshotResponse(
            Long snapshotId,
            Long msmeId,
            LocalDate reportingPeriod,
            BigDecimal revenue,
            BigDecimal totalReceivables,
            BigDecimal overdueReceivables,
            BigDecimal creditUsed,
            Long submittedByUserId,
            LocalDateTime createdAt
            ) {

        this.snapshotId = snapshotId;
        this.msmeId = msmeId;
        this.reportingPeriod = reportingPeriod;
        this.revenue = revenue;
        this.totalReceivables = totalReceivables;
        this.overdueReceivables = overdueReceivables;
        this.creditUsed = creditUsed;
        this.submittedByUserId = submittedByUserId;
        this.createdAt = createdAt;
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

    public void setTotalReceivables(
            BigDecimal totalReceivables) {

        this.totalReceivables = totalReceivables;
    }

    public BigDecimal getOverdueReceivables() {
        return overdueReceivables;
    }

    public void setOverdueReceivables(
            BigDecimal overdueReceivables) {

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

    public void setSubmittedByUserId(
            Long submittedByUserId) {

        this.submittedByUserId = submittedByUserId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(
            LocalDateTime createdAt) {

        this.createdAt = createdAt;
    }

    
}