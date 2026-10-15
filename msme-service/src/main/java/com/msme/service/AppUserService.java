package com.msme.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.msme.dto.user.AppUserResponse;
import com.msme.dto.user.CreateMsmeRepRequest;
import com.msme.dto.user.MsmeDropdownResponse;
import com.msme.dto.user.UpdateMsmeRepRequest;
import com.msme.entity.AppUser;
import com.msme.entity.Msme;
import com.msme.exception.DuplicateResourceException;
import com.msme.exception.ForbiddenOperationException;
import com.msme.exception.ResourceNotFoundException;
import com.msme.repository.AppUserRepository;
import com.msme.repository.MsmeRepository;
import com.msme.security.CustomUserPrincipal;

@Service
@Transactional
public class AppUserService {

    private static final String ROLE_RM = "RM";
    private static final String ROLE_RISK_ADMIN = "RISK_ADMIN";
    private static final String ROLE_MSME_REP = "MSME_REP";

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final AppUserRepository appUserRepository;
    private final MsmeRepository msmeRepository;
    private final PasswordEncoder passwordEncoder;

    public AppUserService(
            AppUserRepository appUserRepository,
            MsmeRepository msmeRepository,
            PasswordEncoder passwordEncoder) {

        this.appUserRepository = appUserRepository;
        this.msmeRepository = msmeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /*
     * =========================================================
     * CURRENT LOGGED-IN USER
     * =========================================================
     */

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public AppUserResponse getCurrentUser() {

        CustomUserPrincipal principal = getCurrentPrincipal();

        AppUser user = appUserRepository.findById(principal.getUserId())
                .orElseThrow(() ->
                    new ResourceNotFoundException(
                        "User not found"
                    )
                );

        return toResponse(user);
    }


    /*
     * =========================================================
     * RISK ADMIN
     * =========================================================
     */

    /**
     * Risk Admin can view every APP_USER record.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('RISK_ADMIN')")
    public List<AppUserResponse> getAllUsers() {

        return appUserRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }


    /**
     * Risk Admin can view all RMs.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('RISK_ADMIN')")
    public List<AppUserResponse> getAllRms() {

        return appUserRepository.findByRole(ROLE_RM)
                .stream()
                .map(this::toResponse)
                .toList();
    }


    /**
     * Risk Admin can view all MSME representatives.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('RISK_ADMIN')")
    public List<AppUserResponse> getAllMsmeRepresentatives() {

        return appUserRepository.findByRole(ROLE_MSME_REP)
                .stream()
                .map(this::toResponse)
                .toList();
    }


    /**
     * Risk Admin can view MSME representatives belonging
     * to MSMEs assigned to a particular RM.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('RISK_ADMIN')")
    public List<AppUserResponse> getMsmeRepresentativesByRm(
            Long rmId) {

        AppUser rm = appUserRepository.findById(rmId)
                .orElseThrow(() ->
                    new ResourceNotFoundException(
                        "RM not found with id: " + rmId
                    )
                );

        if (!ROLE_RM.equals(rm.getRole())) {
            throw new ResourceNotFoundException(
                "RM not found with id: " + rmId
            );
        }

        List<Long> msmeIds = msmeRepository
                .findByAssignedRmId(rmId)
                .stream()
                .map(Msme::getMsmeId)
                .toList();

        if (msmeIds.isEmpty()) {
            return Collections.emptyList();
        }

        return appUserRepository
                .findByRoleAndMsmeIdIn(
                    ROLE_MSME_REP,
                    msmeIds
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }


    /*
     * =========================================================
     * RM - MSME DROPDOWN
     * =========================================================
     */

    /**
     * Returns only MSMEs assigned to the currently logged-in RM.
     *
     * Used by the "Add MSME Representative" frontend dropdown.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('RM')")
    public List<MsmeDropdownResponse> getMyMsmeDropdown() {

        CustomUserPrincipal principal = getCurrentPrincipal();

        Long currentRmId = principal.getUserId();

        return msmeRepository.findByAssignedRmId(currentRmId)
                .stream()
                .map(msme ->
                    new MsmeDropdownResponse(
                        msme.getMsmeId(),
                        msme.getBusinessName()
                    )
                )
                .toList();
    }


    /*
     * =========================================================
     * RM - VIEW OWN MSME REPRESENTATIVES
     * =========================================================
     */

    /**
     * Returns only representatives belonging to MSMEs
     * assigned to the currently logged-in RM.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('RM')")
    public List<AppUserResponse> getMyMsmeRepresentatives() {

        CustomUserPrincipal principal = getCurrentPrincipal();

        Long currentRmId = principal.getUserId();

        List<Long> msmeIds = msmeRepository
                .findByAssignedRmId(currentRmId)
                .stream()
                .map(Msme::getMsmeId)
                .toList();

        if (msmeIds.isEmpty()) {
            return Collections.emptyList();
        }

        return appUserRepository
                .findByRoleAndMsmeIdIn(
                    ROLE_MSME_REP,
                    msmeIds
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }


    /*
     * =========================================================
     * RM - CREATE MSME REPRESENTATIVE
     * =========================================================
     */

    @PreAuthorize("hasRole('RM')")
    public AppUserResponse createMsmeRepresentative(
            CreateMsmeRepRequest request) {

        CustomUserPrincipal principal = getCurrentPrincipal();

        Long currentRmId = principal.getUserId();

        /*
         * Never trust the msmeId simply because frontend sent it.
         */
        Msme msme = msmeRepository.findById(request.getMsmeId())
                .orElseThrow(() ->
                    new ResourceNotFoundException(
                        "MSME not found with id: "
                                + request.getMsmeId()
                    )
                );

        /*
         * Object-level authorization.
         *
         * The logged-in RM may create a representative only
         * for an MSME assigned to that RM.
         */
        if (!currentRmId.equals(msme.getAssignedRmId())) {

            throw new ForbiddenOperationException(
                "You are not authorized to create a representative "
                + "for this MSME"
            );
        }

        /*
         * DB has uq_app_user_msme, but check here first
         * to return a meaningful API response.
         */
        if (appUserRepository.findByMsmeId(msme.getMsmeId())
                .isPresent()) {

            throw new DuplicateResourceException(
                "This MSME already has a representative"
            );
        }

        String normalizedEmail = request.getEmail().trim();

        if (appUserRepository.existsByEmail(normalizedEmail)) {

            throw new DuplicateResourceException(
                "An account already exists with this email"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        AppUser representative = new AppUser();

        representative.setName(request.getName().trim());
        representative.setEmail(normalizedEmail);

        /*
         * Plain password comes from request.
         * Only BCrypt hash is stored in Oracle.
         */
        representative.setPasswordHash(
            passwordEncoder.encode(request.getPassword())
        );

        /*
         * These values are controlled by backend.
         *
         * Frontend is never allowed to choose them.
         */
        representative.setRole(ROLE_MSME_REP);
        representative.setMsmeId(msme.getMsmeId());
        representative.setAccountStatus(STATUS_ACTIVE);

        representative.setCreatedAt(now);
        representative.setUpdatedAt(now);

        AppUser savedUser =
                appUserRepository.save(representative);

        return toResponse(savedUser);
    }


    /*
     * =========================================================
     * RM / RISK ADMIN - VIEW ONE MSME REPRESENTATIVE
     * =========================================================
     */

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('RM', 'RISK_ADMIN')")
    public AppUserResponse getMsmeRepresentative(
            Long representativeUserId) {

        AppUser representative =
                getRepresentativeOrThrow(
                    representativeUserId
                );

        CustomUserPrincipal principal =
                getCurrentPrincipal();

        /*
         * Risk Admin has global read visibility.
         */
        if (ROLE_RISK_ADMIN.equals(principal.getRole())) {
            return toResponse(representative);
        }

        /*
         * Otherwise this request must be from an RM.
         * Verify that the representative's MSME belongs
         * to the logged-in RM.
         */
        verifyRepresentativeBelongsToRm(
                representative,
                principal.getUserId()
        );

        return toResponse(representative);
    }


    /*
     * =========================================================
     * RM - UPDATE OWN MSME REPRESENTATIVE
     * =========================================================
     */

    @PreAuthorize("hasRole('RM')")
    public AppUserResponse updateMsmeRepresentative(
            Long representativeUserId,
            UpdateMsmeRepRequest request) {

        CustomUserPrincipal principal =
                getCurrentPrincipal();

        AppUser representative =
                getRepresentativeOrThrow(
                    representativeUserId
                );

        /*
         * RM can update only representatives belonging
         * to MSMEs assigned to that RM.
         */
        verifyRepresentativeBelongsToRm(
                representative,
                principal.getUserId()
        );

        String normalizedEmail = request.getEmail().trim();

        /*
         * If email changes, make sure another account
         * does not already use the new email.
         */
        if (!representative.getEmail()
                .equalsIgnoreCase(normalizedEmail)) {

            appUserRepository.findByEmail(normalizedEmail)
                    .ifPresent(existingUser -> {

                        if (!existingUser.getUserId()
                                .equals(representative.getUserId())) {

                            throw new DuplicateResourceException(
                                "An account already exists with this email"
                            );
                        }
                    });
        }

        representative.setName(
            request.getName().trim()
        );

        representative.setEmail(
            normalizedEmail
        );

        representative.setUpdatedAt(
            LocalDateTime.now()
        );

        AppUser updatedUser =
                appUserRepository.save(representative);

        return toResponse(updatedUser);
    }


    /*
     * =========================================================
     * INTERNAL SECURITY / OWNERSHIP HELPERS
     * =========================================================
     */

    /**
     * Gets the authenticated application user from
     * Spring Security's SecurityContext.
     *
     * User ID / role are NOT accepted from a request DTO.
     */
    private CustomUserPrincipal getCurrentPrincipal() {

        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                    instanceof CustomUserPrincipal)) {

            throw new AuthenticationCredentialsNotFoundException(
                "Authentication is required"
            );
        }

        return (CustomUserPrincipal)
                authentication.getPrincipal();
    }


    /**
     * Find a user and make sure that user is actually
     * an MSME representative.
     */
    private AppUser getRepresentativeOrThrow(
            Long representativeUserId) {

        AppUser user = appUserRepository
                .findById(representativeUserId)
                .orElseThrow(() ->
                    new ResourceNotFoundException(
                        "MSME representative not found with id: "
                                + representativeUserId
                    )
                );

        if (!ROLE_MSME_REP.equals(user.getRole())) {

            throw new ResourceNotFoundException(
                "MSME representative not found with id: "
                        + representativeUserId
            );
        }

        return user;
    }


    /**
     * Ensures that an MSME representative belongs to
     * an MSME assigned to the specified RM.
     */
    private void verifyRepresentativeBelongsToRm(
            AppUser representative,
            Long rmId) {

        Long msmeId = representative.getMsmeId();

        if (msmeId == null) {

            throw new ForbiddenOperationException(
                "You are not authorized to access "
                + "this MSME representative"
            );
        }

        Msme msme = msmeRepository.findById(msmeId)
                .orElseThrow(() ->
                    new ResourceNotFoundException(
                        "MSME not found with id: " + msmeId
                    )
                );

        if (!rmId.equals(msme.getAssignedRmId())) {

            throw new ForbiddenOperationException(
                "You are not authorized to access "
                + "this MSME representative"
            );
        }
    }


    /*
     * =========================================================
     * ENTITY -> RESPONSE DTO
     * =========================================================
     */

    private AppUserResponse toResponse(
            AppUser user) {

        return new AppUserResponse(
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getMsmeId(),
                user.getAccountStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}