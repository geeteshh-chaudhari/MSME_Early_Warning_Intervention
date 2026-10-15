package com.msme.dto.msme;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UpdateMsmeRequest {

    @NotBlank(message = "Business name is required")
    @Size(
        max = 150,
        message = "Business name must not exceed 150 characters"
    )
    private String businessName;

    @NotBlank(message = "Sector is required")
    @Size(
        max = 50,
        message = "Sector must not exceed 50 characters"
    )
    private String sector;

    @NotBlank(message = "Contact name is required")
    @Size(
        max = 100,
        message = "Contact name must not exceed 100 characters"
    )
    private String contactName;

    @NotNull(message = "Sanctioned credit limit is required")
    @DecimalMin(
        value = "0.01",
        message = "Sanctioned credit limit must be greater than zero"
    )
    private BigDecimal sanctionedCreditLimit;

    @Size(
        max = 20,
        message = "Consent status must not exceed 20 characters"
    )
    private String consentStatus;

    public UpdateMsmeRequest() {
    }

    public UpdateMsmeRequest(
            String businessName,
            String sector,
            String contactName,
            BigDecimal sanctionedCreditLimit,
            String consentStatus) {

        this.businessName = businessName;
        this.sector = sector;
        this.contactName = contactName;
        this.sanctionedCreditLimit = sanctionedCreditLimit;
        this.consentStatus = consentStatus;
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
}