package com.msme.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.msme.dto.msme.CreateMsmeRequest;
import com.msme.dto.msme.MsmeResponse;
import com.msme.dto.msme.UpdateMsmeRequest;
import com.msme.service.MsmeService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/msmes")
public class MsmeController {

    private final MsmeService msmeService;

    public MsmeController(MsmeService msmeService) {
        this.msmeService = msmeService;
    }

    /*
     * =========================================================
     * RISK ADMIN
     * =========================================================
     */

    /**
     * Risk Admin:
     * View all MSMEs.
     */
    @GetMapping
    public ResponseEntity<List<MsmeResponse>> getAllMsmes() {

        List<MsmeResponse> msmes =
                msmeService.getAllMsmes();

        return ResponseEntity.ok(msmes);
    }

    /**
     * Risk Admin:
     * View all MSMEs assigned to a particular RM.
     */
    @GetMapping("/rm/{rmId}")
    public ResponseEntity<List<MsmeResponse>> getMsmesByRm(
            @PathVariable Long rmId) {

        List<MsmeResponse> msmes =
                msmeService.getMsmesByRm(rmId);

        return ResponseEntity.ok(msmes);
    }

    /*
     * =========================================================
     * RM
     * =========================================================
     */

    /**
     * RM:
     * Create a new MSME.
     *
     * The logged-in RM is automatically assigned.
     * assignedRmId is NOT accepted from the request body.
     */
    @PostMapping
    public ResponseEntity<MsmeResponse> createMsme(
            @Valid @RequestBody CreateMsmeRequest request) {

        MsmeResponse createdMsme =
                msmeService.createMsme(request);

        URI location = URI.create(
                "/api/msmes/" + createdMsme.getMsmeId()
        );

        return ResponseEntity
                .created(location)
                .body(createdMsme);
    }

    /**
     * RM:
     * View all MSMEs assigned to the currently logged-in RM.
     */
    @GetMapping("/my")
    public ResponseEntity<List<MsmeResponse>> getMyMsmes() {

        List<MsmeResponse> msmes =
                msmeService.getMyMsmes();

        return ResponseEntity.ok(msmes);
    }

    /**
     * RM:
     * Update one of the MSMEs assigned to the logged-in RM.
     */
    @PutMapping("/{msmeId}")
    public ResponseEntity<MsmeResponse> updateMsme(
            @PathVariable Long msmeId,
            @Valid @RequestBody UpdateMsmeRequest request) {

        MsmeResponse updatedMsme =
                msmeService.updateMsme(
                        msmeId,
                        request
                );

        return ResponseEntity.ok(updatedMsme);
    }

    /**
     * RM:
     * Soft-deactivate one of the MSMEs assigned to the RM.
     *
     * Nothing is deleted.
     * The associated MSME Representative is also made INACTIVE.
     */
    @PatchMapping("/{msmeId}/deactivate")
    public ResponseEntity<MsmeResponse> deactivateMsme(
            @PathVariable Long msmeId) {

        MsmeResponse deactivatedMsme =
                msmeService.deactivateMsme(msmeId);

        return ResponseEntity.ok(deactivatedMsme);
    }

    /*
     * =========================================================
     * MSME REPRESENTATIVE
     * =========================================================
     */

    /**
     * MSME Representative:
     * View the MSME associated with the logged-in account.
     */
    @GetMapping("/me")
    public ResponseEntity<MsmeResponse> getMyMsme() {

        MsmeResponse msme =
                msmeService.getMyMsme();

        return ResponseEntity.ok(msme);
    }

    /*
     * =========================================================
     * SHARED READ
     * =========================================================
     */

    /**
     * Risk Admin:
     *      may view any MSME.
     *
     * RM:
     *      may view only an MSME assigned to them.
     *
     * MSME Representative:
     *      may view only their own MSME.
     *
     * The ownership authorization is performed in MsmeService.
     */
    @GetMapping("/{msmeId}")
    public ResponseEntity<MsmeResponse> getMsmeById(
            @PathVariable Long msmeId) {

        MsmeResponse msme =
                msmeService.getMsmeById(msmeId);

        return ResponseEntity.ok(msme);
    }
}