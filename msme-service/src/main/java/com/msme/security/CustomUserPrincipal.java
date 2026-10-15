package com.msme.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.msme.entity.AppUser;

public class CustomUserPrincipal implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String name;
    private final String email;
    private final String password;
    private final String role;
    private final Long msmeId;
    private final boolean active;

    public CustomUserPrincipal(AppUser user) {

        this.userId = user.getUserId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.password = user.getPasswordHash();
        this.role = user.getRole();
        this.msmeId = user.getMsmeId();

        this.active = "ACTIVE".equalsIgnoreCase(user.getAccountStatus());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {

        return List.of(
            new SimpleGrantedAuthority("ROLE_" + role)
        );
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public Long getMsmeId() {
        return msmeId;
    }
}