package com.msme.service;

import java.time.*;
import java.util.*;
import com.msme.dto.intervention.*;
import com.msme.dto.followup.*;
import com.msme.entity.*;
import com.msme.exception.*;
import com.msme.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InterventionService {
    private final InterventionRepository interventions;
    private final FollowUpReviewRepository reviews;
    private final FinancialSnapshotRepository snapshots;
    private final WarningService warnings;
    private final CaseAccessService access;

    public InterventionService(InterventionRepository interventions, FollowUpReviewRepository reviews,
            FinancialSnapshotRepository snapshots, WarningService warnings, CaseAccessService access) {
        this.interventions = interventions; this.reviews = reviews; this.snapshots = snapshots;
        this.warnings = warnings; this.access = access;
    }

    @PreAuthorize("hasRole('RM')")
    public InterventionResponse createIntervention(Long warningId, CreateInterventionRequest request) {
        access.requireRole("RM");
        var warning = warnings.writable(warningId);
        WarningService.requireState(warning, "UNDER_REVIEW", "INTERVENTION_ACTIVE");
        validateDate(request.getFollowUpDate());
        String prior = warning.getWarningStatus();
        var active = interventions.findByWarningIdAndIsActive(warningId, "Y");
        // Revising an active intervention preserves its reviews and starts a further cycle.
        if (!active.isEmpty()) {
            for (var old : active) {
                var last = latestReview(old.getInterventionId());
                if (last == null || !"STILL_STRESSED".equals(last.getOutcome())) {
                    throw new DuplicateResourceException("An intervention may be revised after a STILL_STRESSED review");
                }
                closeIntervention(old);
            }
        }
        var intervention = new Intervention();
        intervention.setWarningId(warningId);
        intervention.setCreatedByUserId(access.currentUser().getUserId());
        intervention.setActionText(request.getActionText().trim());
        intervention.setNotes(request.getNotes());
        intervention.setFollowUpDate(request.getFollowUpDate());
        intervention.setIsActive("Y");
        intervention.setCreatedAt(LocalDateTime.now());
        interventions.saveAndFlush(intervention);
        warning.setWarningStatus("INTERVENTION_ACTIVE");
        warnings.audit(warning, active.isEmpty() ? "INTERVENTION_CREATED" : "INTERVENTION_REVISED",
                "Intervention " + intervention.getInterventionId() + ", follow-up " + intervention.getFollowUpDate()
                        + ": " + intervention.getActionText() + "; notes: " + Objects.toString(intervention.getNotes(), ""),
                prior, warning.getWarningStatus());
        return ResponseMapper.toResponse(intervention);
    }

    @PreAuthorize("hasRole('RM')")
    public InterventionResponse updateIntervention(Long id, UpdateInterventionRequest request) {
        access.requireRole("RM");
        var warning = warnings.writable(warningId(id));
        var intervention = intervention(id);
        WarningService.requireState(warning, "INTERVENTION_ACTIVE");
        requireActive(intervention);
        validateDate(request.getFollowUpDate());
        intervention.setNotes(request.getNotes().trim());
        intervention.setFollowUpDate(request.getFollowUpDate());
        warnings.audit(warning, "INTERVENTION_UPDATED", "Intervention " + id + ", follow-up "
                + request.getFollowUpDate() + ": " + request.getNotes(),
                warning.getWarningStatus(), warning.getWarningStatus());
        return ResponseMapper.toResponse(intervention);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('RM', 'RISK_ADMIN', 'MSME_REP')")
    public Page<InterventionResponse> getInterventions(Long warningId, int page, int size) {
        warnings.readable(warningId);
        return interventions.findByWarningId(warningId, WarningService.page(page, size, "interventionId"))
                .map(ResponseMapper::toResponse);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('RM', 'RISK_ADMIN', 'MSME_REP')")
    public Page<FollowUpReviewResponse> getFollowUps(Long id, int page, int size) {
        var intervention = intervention(id);
        warnings.readable(intervention.getWarningId());
        return reviews.findByInterventionId(id, WarningService.page(page, size, "followupId"))
                .map(ResponseMapper::toResponse);
    }

    @PreAuthorize("hasRole('RM')")
    public FollowUpReviewResponse followUp(Long id, CreateFollowUpRequest request) {
        access.requireRole("RM");
        // Read only the parent ID before locking; reload the intervention after taking the case lock.
        var warning = warnings.writable(warningId(id));
        WarningService.requireState(warning, "INTERVENTION_ACTIVE");
        var intervention = intervention(id);
        requireActive(intervention);
        var current = warnings.assessment(request.getAssessmentId());
        var baseline = warnings.assessment(warning.getAssessmentId());
        if (!current.getMsmeId().equals(baseline.getMsmeId())) {
            throw new InvalidRequestException("Follow-up assessment must belong to the same MSME");
        }
        var period = period(current);
        if (!period.isAfter(period(baseline)) || !period.isAfter(intervention.getCreatedAt().toLocalDate().withDayOfMonth(1))) {
            throw new InvalidRequestException("Follow-up requires a later reporting month than the baseline and intervention creation month");
        }
        if (period.isAfter(LocalDate.now().withDayOfMonth(1)) || current.getAssessmentDate().isAfter(LocalDateTime.now())) {
            throw new InvalidRequestException("Future assessment evidence cannot be reviewed");
        }
        var previous = reviews.findByInterventionId(id);
        if (previous.stream().anyMatch(r -> !period.isAfter(period(warnings.assessment(r.getAssessmentId()))))) {
            throw new DuplicateResourceException("A follow-up must use a new reporting month after all previous reviews");
        }
        if (reviews.existsByInterventionIdAndAssessmentId(id, request.getAssessmentId())) {
            throw new DuplicateResourceException("Assessment already reviewed for this intervention");
        }
        boolean keepActive = Boolean.TRUE.equals(request.getKeepInterventionActive());
        if (!keepActive && !Set.of("IMPROVING", "STABILISING").contains(request.getOutcome())) {
            throw new InvalidRequestException("Only improving or stabilising outcomes may close a case");
        }
        if (keepActive) validateDate(request.getNextFollowUpDate());
        else if (request.getNextFollowUpDate() != null) throw new InvalidRequestException("A closed case cannot have a next follow-up date");
        var review = new FollowUpReview();
        review.setInterventionId(id);
        review.setAssessmentId(current.getAssessmentId());
        review.setReviewedByUserId(access.currentUser().getUserId());
        review.setOutcome(request.getOutcome());
        review.setReviewNotes(request.getReviewNotes().trim());
        review.setReviewedAt(LocalDateTime.now());
        review.setKeepInterventionActive(keepActive ? "Y" : "N");
        reviews.saveAndFlush(review);
        String prior = warning.getWarningStatus();
        if (!keepActive) {
            warning.setWarningStatus("CLOSED");
            warnings.closeInterventions(warning);
        } else {
            intervention.setFollowUpDate(request.getNextFollowUpDate());
            if ("ESCALATED".equals(request.getOutcome())) warning.setWarningStatus("ESCALATED");
        }
        warnings.audit(warning, "FOLLOW_UP_" + request.getOutcome(),
                "Intervention " + id + ", assessment " + current.getAssessmentId() + ", next follow-up "
                        + request.getNextFollowUpDate() + ": " + request.getReviewNotes(),
                prior, warning.getWarningStatus());
        return ResponseMapper.toResponse(review);
    }

    private Intervention intervention(Long id) {
        return interventions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Intervention not found"));
    }

    private Long warningId(Long id) {
        return interventions.findWarningId(id).orElseThrow(() -> new ResourceNotFoundException("Intervention not found"));
    }

    private FollowUpReview latestReview(Long id) {
        return reviews.findByInterventionId(id).stream()
                .max(Comparator.comparing(FollowUpReview::getReviewedAt)).orElse(null);
    }

    private LocalDate period(RiskAssessment assessment) {
        var snapshot = snapshots.findById(assessment.getSnapshotId())
                .orElseThrow(() -> new ResourceNotFoundException("Assessment snapshot not found"));
        if (!snapshot.getMsmeId().equals(assessment.getMsmeId())) throw new InvalidRequestException("Assessment snapshot MSME mismatch");
        return snapshot.getReportingPeriod();
    }

    private static void requireActive(Intervention intervention) {
        if (!"Y".equals(intervention.getIsActive())) throw new DuplicateResourceException("Intervention is closed");
    }

    private static void validateDate(LocalDate date) {
        if (date == null || date.isBefore(LocalDate.now())) throw new InvalidRequestException("Follow-up date must be today or later");
    }

    private void closeIntervention(Intervention intervention) {
        intervention.setIsActive("N");
        intervention.setClosedAt(LocalDateTime.now());
        intervention.setClosedByUserId(access.currentUser().getUserId());
    }
}
