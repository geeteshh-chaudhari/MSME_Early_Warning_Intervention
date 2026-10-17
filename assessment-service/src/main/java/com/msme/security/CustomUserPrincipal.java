package com.msme.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class CustomUserPrincipal implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String role;
    private final Long msmeId;
    private final boolean active;

    public CustomUserPrincipal(
            Long userId,
            String role,
            Long msmeId,
            boolean active) {

        this.userId = userId;
        this.role = role;
        this.msmeId = msmeId;
        this.active = active;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(
                new SimpleGrantedAuthority("ROLE_" + role)
        );
    }

    @Override
    public String getUsername() {
        return userId.toString();
    }

    @Override
    public String getPassword() {
        // MS2 authenticates existing JWTs, not passwords.
        return null;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public Long getMsmeId() {
        return msmeId;
    }
}