package com.msme.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.msme.dto.msme.CreateMsmeRequest;
import com.msme.dto.msme.MsmeResponse;
import com.msme.dto.msme.UpdateMsmeRequest;
import com.msme.entity.AppUser;
import com.msme.entity.Msme;
import com.msme.exception.ForbiddenOperationException;
import com.msme.exception.InvalidRequestException;
import com.msme.exception.ResourceNotFoundException;
import com.msme.repository.AppUserRepository;
import com.msme.repository.MsmeRepository;
import com.msme.security.CustomUserPrincipal;

@Service
@Transactional
public class MsmeService {

    private static final String ROLE_RISK_ADMIN = "RISK_ADMIN";
    private static final String ROLE_RM = "RM";
    private static final String ROLE_MSME_REP = "MSME_REP";

    private static final String MSME_ACTIVE = "Y";
    private static final String MSME_INACTIVE = "N";

    private static final String USER_INACTIVE = "INACTIVE";

    private final MsmeRepository msmeRepository;
    private final AppUserRepository appUserRepository;

    public MsmeService(
            MsmeRepository msmeRepository,
            AppUserRepository appUserRepository) {

        this.msmeRepository = msmeRepository;
        this.appUserRepository = appUserRepository;
    }

    /*
     * =========================================================
     * RISK ADMIN - VIEW ALL MSMEs
     * =========================================================
     */

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('RISK_ADMIN')")
    public List<MsmeResponse> getAllMsmes() {

        return msmeRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /*
     * =========================================================
     * RISK ADMIN - VIEW MSMEs OF A PARTICULAR RM
     * =========================================================
     */

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('RISK_ADMIN')")
    public List<MsmeResponse> getMsmesByRm(Long rmId) {

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

        return msmeRepository.findByAssignedRmId(rmId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /*
     * =========================================================
     * RM - CREATE MSME
     * =========================================================
     */

    @PreAuthorize("hasRole('RM')")
    public MsmeResponse createMsme(
            CreateMsmeRequest request) {

        CustomUserPrincipal principal =
                getCurrentPrincipal();

        Long currentRmId =
                principal.getUserId();

        LocalDateTime now =
                LocalDateTime.now();

        Msme msme = new Msme();

        msme.setBusinessName(
            request.getBusinessName().trim()
        );

        msme.setSector(
            request.getSector().trim()
        );

        msme.setContactName(
            request.getContactName().trim()
        );

        msme.setSanctionedCreditLimit(
            request.getSanctionedCreditLimit()
        );

        if (request.getConsentStatus() != null) {

            msme.setConsentStatus(
                request.getConsentStatus().trim()
            );
        }

        /*
         * IMPORTANT:
         *
         * RM ID is never accepted from the request.
         * The authenticated RM automatically becomes
         * the assigned RM.
         */
        msme.setAssignedRmId(currentRmId);

        /*
         * Newly created MSMEs are active.
         */
        msme.setIsActive(MSME_ACTIVE);

        msme.setCreatedAt(now);
        msme.setUpdatedAt(now);

        Msme savedMsme =
                msmeRepository.save(msme);

        return toResponse(savedMsme);
    }

    /*
     * =========================================================
     * RM - VIEW OWN MSMEs
     * =========================================================
     */

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('RM')")
    public List<MsmeResponse> getMyMsmes() {

        CustomUserPrincipal principal =
                getCurrentPrincipal();

        return msmeRepository
                .findByAssignedRmId(
                    principal.getUserId()
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /*
     * =========================================================
     * MSME REPRESENTATIVE - VIEW OWN MSME
     * =========================================================
     */

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MSME_REP')")
    public MsmeResponse getMyMsme() {

        CustomUserPrincipal principal =
                getCurrentPrincipal();

        Long msmeId =
                principal.getMsmeId();

        if (msmeId == null) {

            throw new ForbiddenOperationException(
                "No MSME is associated with this account"
            );
        }

        Msme msme = msmeRepository.findById(msmeId)
                .orElseThrow(() ->
                    new ResourceNotFoundException(
                        "MSME not found with id: " + msmeId
                    )
                );

        return toResponse(msme);
    }

    /*
     * =========================================================
     * VIEW ONE MSME
     *
     * RISK_ADMIN:
     *     any MSME
     *
     * RM:
     *     own assigned MSME only
     *
     * MSME_REP:
     *     own MSME only
     * =========================================================
     */

    @Transactional(readOnly = true)
    @PreAuthorize(
        "hasAnyRole('RISK_ADMIN', 'RM', 'MSME_REP')"
    )
    public MsmeResponse getMsmeById(
            Long msmeId) {

        CustomUserPrincipal principal =
                getCurrentPrincipal();

        Msme msme = getMsmeOrThrow(msmeId);

        authorizeMsmeRead(
                principal,
                msme
        );

        return toResponse(msme);
    }

    /*
     * =========================================================
     * RM - UPDATE OWN MSME
     * =========================================================
     */

    @PreAuthorize("hasRole('RM')")
    public MsmeResponse updateMsme(
            Long msmeId,
            UpdateMsmeRequest request) {

        CustomUserPrincipal principal =
                getCurrentPrincipal();

        Msme msme =
                getMsmeOrThrow(msmeId);

        verifyMsmeBelongsToRm(
                msme,
                principal.getUserId()
        );

        /*
         * We do not allow updates to a deactivated MSME.
         */
        if (MSME_INACTIVE.equals(msme.getIsActive())) {

            throw new InvalidRequestException(
                "Deactivated MSME cannot be updated"
            );
        }

        msme.setBusinessName(
            request.getBusinessName().trim()
        );

        msme.setSector(
            request.getSector().trim()
        );

        msme.setContactName(
            request.getContactName().trim()
        );

        msme.setSanctionedCreditLimit(
            request.getSanctionedCreditLimit()
        );

        if (request.getConsentStatus() != null) {

            msme.setConsentStatus(
                request.getConsentStatus().trim()
            );

        } else {

            msme.setConsentStatus(null);
        }

        msme.setUpdatedAt(
            LocalDateTime.now()
        );

        Msme updatedMsme =
                msmeRepository.save(msme);

        return toResponse(updatedMsme);
    }

    /*
     * =========================================================
     * RM - DEACTIVATE OWN MSME
     * =========================================================
     */

    @PreAuthorize("hasRole('RM')")
    public MsmeResponse deactivateMsme(
            Long msmeId) {

        CustomUserPrincipal principal =
                getCurrentPrincipal();

        Msme msme =
                getMsmeOrThrow(msmeId);

        /*
         * RM can deactivate only an MSME assigned to them.
         */
        verifyMsmeBelongsToRm(
                msme,
                principal.getUserId()
        );

        if (MSME_INACTIVE.equals(msme.getIsActive())) {

            throw new InvalidRequestException(
                "MSME is already inactive"
            );
        }

        msme.setIsActive(MSME_INACTIVE);
        msme.setUpdatedAt(LocalDateTime.now());

        /*
         * If this MSME already has a representative,
         * deactivate that user's account as well.
         *
         * Nothing is deleted.
         */
        appUserRepository.findByMsmeId(msmeId)
                .ifPresent(representative -> {

                    representative.setAccountStatus(
                        USER_INACTIVE
                    );

                    representative.setUpdatedAt(
                        LocalDateTime.now()
                    );

                    appUserRepository.save(
                        representative
                    );
                });

        Msme updatedMsme =
                msmeRepository.save(msme);

        return toResponse(updatedMsme);
    }

    /*
     * =========================================================
     * AUTHORIZATION HELPERS
     * =========================================================
     */

    private void authorizeMsmeRead(
            CustomUserPrincipal principal,
            Msme msme) {

        String role =
                principal.getRole();

        /*
         * Risk Admin has global read visibility.
         */
        if (ROLE_RISK_ADMIN.equals(role)) {
            return;
        }

        /*
         * RM may read only MSMEs assigned to them.
         */
        if (ROLE_RM.equals(role)) {

            verifyMsmeBelongsToRm(
                    msme,
                    principal.getUserId()
            );

            return;
        }

        /*
         * MSME Representative may read only
         * their own MSME.
         */
        if (ROLE_MSME_REP.equals(role)) {

            Long principalMsmeId =
                    principal.getMsmeId();

            if (principalMsmeId == null
                    || !principalMsmeId.equals(
                        msme.getMsmeId()
                    )) {

                throw new ForbiddenOperationException(
                    "You are not authorized to access this MSME"
                );
            }

            return;
        }

        throw new ForbiddenOperationException(
            "You are not authorized to access MSME information"
        );
    }

    /*
     * Ensures the MSME belongs to the specified RM.
     */
    private void verifyMsmeBelongsToRm(
            Msme msme,
            Long rmId) {

        if (!rmId.equals(
                msme.getAssignedRmId()
        )) {

            throw new ForbiddenOperationException(
                "You are not authorized to access this MSME"
            );
        }
    }

    /*
     * =========================================================
     * ENTITY LOOKUP
     * =========================================================
     */

    private Msme getMsmeOrThrow(
            Long msmeId) {

        return msmeRepository.findById(msmeId)
                .orElseThrow(() ->
                    new ResourceNotFoundException(
                        "MSME not found with id: "
                                + msmeId
                    )
                );
    }

    /*
     * =========================================================
     * CURRENT AUTHENTICATED USER
     * =========================================================
     */

    private CustomUserPrincipal getCurrentPrincipal() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
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

    /*
     * =========================================================
     * ENTITY -> DTO
     * =========================================================
     */

    private MsmeResponse toResponse(
            Msme msme) {

        return new MsmeResponse(
                msme.getMsmeId(),
                msme.getBusinessName(),
                msme.getSector(),
                msme.getContactName(),
                msme.getSanctionedCreditLimit(),
                msme.getConsentStatus(),
                msme.getAssignedRmId(),
                msme.getIsActive(),
                msme.getCreatedAt(),
                msme.getUpdatedAt()
        );
    }
}