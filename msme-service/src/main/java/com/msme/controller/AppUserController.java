package com.msme.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.msme.dto.user.AppUserResponse;
import com.msme.dto.user.CreateMsmeRepRequest;
import com.msme.dto.user.MsmeDropdownResponse;
import com.msme.dto.user.UpdateMsmeRepRequest;
import com.msme.service.AppUserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class AppUserController {

    private final AppUserService appUserService;

    public AppUserController(
            AppUserService appUserService) {

        this.appUserService = appUserService;
    }

    /*
     * =========================================================
     * CURRENT LOGGED-IN USER
     * =========================================================
     */

    /**
     * Any authenticated user can view their own profile.
     */
    @GetMapping("/me")
    public ResponseEntity<AppUserResponse> getCurrentUser() {

        AppUserResponse response =
                appUserService.getCurrentUser();

        return ResponseEntity.ok(response);
    }


    /*
     * =========================================================
     * RISK ADMIN ENDPOINTS
     * =========================================================
     */

    /**
     * Risk Admin:
     * View all application users.
     */
    @GetMapping
    public ResponseEntity<List<AppUserResponse>> getAllUsers() {

        List<AppUserResponse> users =
                appUserService.getAllUsers();

        return ResponseEntity.ok(users);
    }


    /**
     * Risk Admin:
     * View all Relationship Managers.
     */
    @GetMapping("/rms")
    public ResponseEntity<List<AppUserResponse>> getAllRms() {

        List<AppUserResponse> rms =
                appUserService.getAllRms();

        return ResponseEntity.ok(rms);
    }


    /**
     * Risk Admin:
     * View all MSME Representatives.
     */
    @GetMapping("/msme-representatives")
    public ResponseEntity<List<AppUserResponse>>
            getAllMsmeRepresentatives() {

        List<AppUserResponse> representatives =
                appUserService.getAllMsmeRepresentatives();

        return ResponseEntity.ok(representatives);
    }


    /**
     * Risk Admin:
     * View MSME Representatives belonging to MSMEs
     * assigned to a specific RM.
     */
    @GetMapping("/rms/{rmId}/msme-representatives")
    public ResponseEntity<List<AppUserResponse>>
            getMsmeRepresentativesByRm(
                    @PathVariable Long rmId) {

        List<AppUserResponse> representatives =
                appUserService.getMsmeRepresentativesByRm(rmId);

        return ResponseEntity.ok(representatives);
    }


    /*
     * =========================================================
     * RM ENDPOINTS
     * =========================================================
     */

    /**
     * Logged-in RM:
     * Returns MSMEs assigned to that RM.
     *
     * Intended for the MSME dropdown shown while
     * creating an MSME Representative.
     */
    @GetMapping("/my-msmes")
    public ResponseEntity<List<MsmeDropdownResponse>>
            getMyMsmeDropdown() {

        List<MsmeDropdownResponse> msmes =
                appUserService.getMyMsmeDropdown();

        return ResponseEntity.ok(msmes);
    }


    /**
     * Logged-in RM:
     * View all MSME Representatives associated with
     * MSMEs assigned to that RM.
     */
    @GetMapping("/my-msme-representatives")
    public ResponseEntity<List<AppUserResponse>>
            getMyMsmeRepresentatives() {

        List<AppUserResponse> representatives =
                appUserService.getMyMsmeRepresentatives();

        return ResponseEntity.ok(representatives);
    }


    /**
     * Logged-in RM:
     * Create an MSME Representative for one of the RM's
     * assigned MSMEs.
     */
    @PostMapping("/msme-representatives")
    public ResponseEntity<AppUserResponse>
            createMsmeRepresentative(
                    @Valid
                    @RequestBody
                    CreateMsmeRepRequest request) {

        AppUserResponse createdUser =
                appUserService.createMsmeRepresentative(
                        request
                );

        URI location = URI.create(
                "/api/users/msme-representatives/"
                        + createdUser.getUserId()
        );

        return ResponseEntity
                .created(location)
                .body(createdUser);
    }


    /*
     * =========================================================
     * RM / RISK ADMIN
     * =========================================================
     */

    /**
     * Risk Admin:
     * Can view any MSME Representative.
     *
     * RM:
     * Can view only representatives belonging to
     * MSMEs assigned to that RM.
     */
    @GetMapping("/msme-representatives/{userId}")
    public ResponseEntity<AppUserResponse>
            getMsmeRepresentative(
                    @PathVariable Long userId) {

        AppUserResponse representative =
                appUserService.getMsmeRepresentative(
                        userId
                );

        return ResponseEntity.ok(representative);
    }


    /**
     * Logged-in RM:
     * Update an MSME Representative belonging to
     * one of that RM's assigned MSMEs.
     *
     * Current scope allows updating:
     * - name
     * - email
     *
     * It does not change:
     * - role
     * - MSME assignment
     * - account status
     * - password
     */
    @PutMapping("/msme-representatives/{userId}")
    public ResponseEntity<AppUserResponse>
            updateMsmeRepresentative(
                    @PathVariable Long userId,
                    @Valid
                    @RequestBody
                    UpdateMsmeRepRequest request) {

        AppUserResponse updatedUser =
                appUserService.updateMsmeRepresentative(
                        userId,
                        request
                );

        return ResponseEntity.ok(updatedUser);
    }
}