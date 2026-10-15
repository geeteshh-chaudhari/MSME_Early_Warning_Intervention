package com.msme.dto.auth;

public class LoginResponse {

    private String accessToken;
    private String tokenType;

    private Long userId;
    private String name;
    private String email;
    private String role;
    private Long msmeId;

    public LoginResponse() {
    }

    public LoginResponse(
            String accessToken,
            String tokenType,
            Long userId,
            String name,
            String email,
            String role,
            Long msmeId) {

        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = role;
        this.msmeId = msmeId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
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
}