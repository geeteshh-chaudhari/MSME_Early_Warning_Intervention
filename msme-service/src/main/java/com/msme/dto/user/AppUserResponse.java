package com.msme.dto.user;

import java.time.LocalDateTime;

public class AppUserResponse {

    private Long userId;
    private String name;
    private String email;
    private String role;
    private Long msmeId;
    private String accountStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AppUserResponse() {
    }

    public AppUserResponse(
            Long userId,
            String name,
            String email,
            String role,
            Long msmeId,
            String accountStatus,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = role;
        this.msmeId = msmeId;
        this.accountStatus = accountStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getMsmeId() {
        return msmeId;
    }

    public void setMsmeId(Long msmeId) {
        this.msmeId = msmeId;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
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