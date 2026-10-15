package com.msme.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@org.hibernate.annotations.Immutable
@Entity
@Table(
    name = "APP_USER",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "UQ_APP_USER_EMAIL",
            columnNames = "EMAIL"
        ),
        @UniqueConstraint(
            name = "UQ_APP_USER_MSME",
            columnNames = "MSME_ID"
        )
    }
)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "EMAIL", nullable = false, length = 150)
    private String email;

    @Column(name = "PASSWORD_HASH", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "ROLE", nullable = false, length = 20)
    private String role;

    @Column(name = "MSME_ID")
    private Long msmeId;

    @Column(name = "ACCOUNT_STATUS", nullable = false, length = 20)
    private String accountStatus;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;


    public AppUser() {
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
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
