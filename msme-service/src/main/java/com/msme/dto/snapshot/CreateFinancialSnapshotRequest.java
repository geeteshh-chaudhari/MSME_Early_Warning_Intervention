package com.msme.dto.snapshot;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public class CreateFinancialSnapshotRequest {

    @NotNull(message = "Reporting period is required")
    private LocalDate reportingPeriod;

    @NotNull(message = "Revenue is required")
    @DecimalMin(
        value = "0.00",
        message = "Revenue cannot be negative"
    )
    private BigDecimal revenue;

    @NotNull(message = "Total receivables is required")
    @DecimalMin(
        value = "0.00",
        message = "Total receivables cannot be negative"
    )
    private BigDecimal totalReceivables;

    @NotNull(message = "Overdue receivables is required")
    @DecimalMin(
        value = "0.00",
        message = "Overdue receivables cannot be negative"
    )
    private BigDecimal overdueReceivables;

    @NotNull(message = "Credit used is required")
    @DecimalMin(
        value = "0.00",
        message = "Credit used cannot be negative"
    )
    private BigDecimal creditUsed;

    public CreateFinancialSnapshotRequest() {
    }

    public CreateFinancialSnapshotRequest(
            LocalDate reportingPeriod,
            BigDecimal revenue,
            BigDecimal totalReceivables,
            BigDecimal overdueReceivables,
            BigDecimal creditUsed) {

        this.reportingPeriod = reportingPeriod;
        this.revenue = revenue;
        this.totalReceivables = totalReceivables;
        this.overdueReceivables = overdueReceivables;
        this.creditUsed = creditUsed;
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
}