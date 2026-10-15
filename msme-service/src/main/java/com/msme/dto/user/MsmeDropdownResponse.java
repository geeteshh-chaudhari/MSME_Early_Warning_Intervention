package com.msme.dto.user;

public class MsmeDropdownResponse {

    private Long msmeId;
    private String businessName;

    public MsmeDropdownResponse() {
    }

    public MsmeDropdownResponse(
            Long msmeId,
            String businessName) {

        this.msmeId = msmeId;
        this.businessName = businessName;
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
}