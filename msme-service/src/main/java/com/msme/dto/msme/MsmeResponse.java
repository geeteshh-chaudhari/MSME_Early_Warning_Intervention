package com.msme.dto.msme;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MsmeResponse {

    private Long msmeId;

    private String businessName;

    private String sector;

    private String contactName;

    private BigDecimal sanctionedCreditLimit;

    private String consentStatus;

    private Long assignedRmId;

    private String isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public MsmeResponse() {
    }

    public MsmeResponse(
            Long msmeId,
            String businessName,
            String sector,
            String contactName,
            BigDecimal sanctionedCreditLimit,
            String consentStatus,
            Long assignedRmId,
            String isActive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        this.msmeId = msmeId;
        this.businessName = businessName;
        this.sector = sector;
        this.contactName = contactName;
        this.sanctionedCreditLimit = sanctionedCreditLimit;
        this.consentStatus = consentStatus;
        this.assignedRmId = assignedRmId;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public BigDecimal getSanctionedCreditLimit() {
        return sanctionedCreditLimit;
    }

    public void setSanctionedCreditLimit(
            BigDecimal sanctionedCreditLimit) {

        this.sanctionedCreditLimit = sanctionedCreditLimit;
    }

    public String getConsentStatus() {
        return consentStatus;
    }

    public void setConsentStatus(String consentStatus) {
        this.consentStatus = consentStatus;
    }

    public Long getAssignedRmId() {
        return assignedRmId;
    }

    public void setAssignedRmId(Long assignedRmId) {
        this.assignedRmId = assignedRmId;
    }

    public String getIsActive() {
        return isActive;
    }

    public void setIsActive(String isActive) {
        this.isActive = isActive;
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