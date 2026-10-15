package com.msme.service;

import com.msme.entity.*;
import com.msme.exception.*;
import com.msme.repository.*;
import com.msme.security.CustomUserPrincipal;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CaseAccessService {
    private final MsmeRepository msmes;

    public CaseAccessService(MsmeRepository msmes) { this.msmes = msmes; }

    public CustomUserPrincipal currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof CustomUserPrincipal user) || !user.isEnabled()) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        }
        return user;
    }

    public void authorize(Long msmeId, boolean write) {
        var user = currentUser();
        var msme = msmes.findById(msmeId)
                .orElseThrow(() -> new ResourceNotFoundException("MSME not found"));
        boolean allowed = switch (user.getRole()) {
            case "RISK_ADMIN" -> true;
            case "RM" -> user.getUserId().equals(msme.getAssignedRmId());
            case "MSME_REP" -> !write && msmeId.equals(user.getMsmeId());
            default -> false;
        };
        if (!allowed) throw new ForbiddenOperationException("You cannot access this MSME case");
        if (write && !"Y".equals(msme.getIsActive())) {
            throw new InvalidRequestException("Inactive MSME cases cannot be changed");
        }
    }

    public void requireRole(String role) {
        if (!role.equals(currentUser().getRole())) {
            throw new ForbiddenOperationException("This action requires role " + role);
        }
    }
}
