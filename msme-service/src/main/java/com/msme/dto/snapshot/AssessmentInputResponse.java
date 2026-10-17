package com.msme.dto.snapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class AssessmentInputResponse {

    private Long msmeId;
    private String businessName;
    private BigDecimal sanctionedCreditLimit;
    private LocalDate reportingPeriod;
    private List<FinancialSnapshotResponse> snapshots;

    public AssessmentInputResponse() {
    }

    public AssessmentInputResponse(
            Long msmeId,
            String businessName,
            BigDecimal sanctionedCreditLimit,
            LocalDate reportingPeriod,
            List<FinancialSnapshotResponse> snapshots) {

        this.msmeId = msmeId;
        this.businessName = businessName;
        this.sanctionedCreditLimit = sanctionedCreditLimit;
        this.reportingPeriod = reportingPeriod;
        this.snapshots = snapshots;
    }
    

    public Long getMsmeId() {
        return msmeId;
    }

    public void setMsmeId(Long msmeId) {
        this.msmeId = msmeId;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public BigDecimal getSanctionedCreditLimit() {
        return sanctionedCreditLimit;
    }

    public void setSanctionedCreditLimit(
            BigDecimal sanctionedCreditLimit) {
        this.sanctionedCreditLimit = sanctionedCreditLimit;
    }

    public LocalDate getReportingPeriod() {
        return reportingPeriod;
    }

    public void setReportingPeriod(
            LocalDate reportingPeriod) {
        this.reportingPeriod = reportingPeriod;
    }

    public List<FinancialSnapshotResponse> getSnapshots() {
        return snapshots;
    }

    public void setSnapshots(
            List<FinancialSnapshotResponse> snapshots) {
        this.snapshots = snapshots;
    }
}