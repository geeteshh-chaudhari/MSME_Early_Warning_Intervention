package com.msme.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;

@org.hibernate.annotations.Immutable
@Entity
@Table(name = "MSME")
public class Msme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MSME_ID")
    private Long msmeId;

    @Column(name = "BUSINESS_NAME", nullable = false, length = 150)
    private String businessName;

    @Column(name = "SECTOR", nullable = false, length = 50)
    private String sector;

    @Column(name = "CONTACT_NAME", nullable = false, length = 100)
    private String contactName;

    @Column(name = "SANCTIONED_CREDIT_LIMIT",
            nullable = false, precision = 18, scale = 2)
    private BigDecimal sanctionedCreditLimit;

    @Column(name = "CONSENT_STATUS", length = 20)
    private String consentStatus;

    @Column(name = "ASSIGNED_RM_ID", nullable = false)
    private Long assignedRmId;

    @Column(name = "IS_ACTIVE", nullable = false, length = 1)
    private String isActive;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    public Msme() {
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

    public void setSanctionedCreditLimit(BigDecimal sanctionedCreditLimit) {
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
