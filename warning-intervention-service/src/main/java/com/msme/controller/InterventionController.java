package com.msme.controller;

import java.net.URI;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.msme.dto.intervention.*;
import com.msme.dto.followup.*;
import com.msme.service.InterventionService;

@RestController
@RequestMapping("/api")
public class InterventionController {
    private final InterventionService interventionService;
    public InterventionController(InterventionService interventionService) { this.interventionService = interventionService; }

    @PostMapping("/warnings/{warningId}/interventions")
    public ResponseEntity<InterventionResponse> create(@PathVariable Long warningId,
            @Valid @RequestBody CreateInterventionRequest request) {
        var result = interventionService.createIntervention(warningId, request);
        return ResponseEntity.created(URI.create("/api/warnings/" + warningId + "/interventions")).body(result);
    }

    @GetMapping("/warnings/{warningId}/interventions")
    public ResponseEntity<Page<InterventionResponse>> list(@PathVariable Long warningId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(interventionService.getInterventions(warningId, page, size));
    }

    @PatchMapping("/interventions/{id}")
    public ResponseEntity<InterventionResponse> update(@PathVariable Long id,
            @Valid @RequestBody UpdateInterventionRequest request) {
        return ResponseEntity.ok(interventionService.updateIntervention(id, request));
    }

    @PostMapping("/interventions/{id}/follow-ups")
    public ResponseEntity<FollowUpReviewResponse> review(@PathVariable Long id,
            @Valid @RequestBody CreateFollowUpRequest request) {
        var result = interventionService.followUp(id, request);
        return ResponseEntity.created(URI.create("/api/interventions/" + id + "/follow-ups")).body(result);
    }

    @GetMapping("/interventions/{id}/follow-ups")
    public ResponseEntity<Page<FollowUpReviewResponse>> reviews(@PathVariable Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(interventionService.getFollowUps(id, page, size));
    }
}
