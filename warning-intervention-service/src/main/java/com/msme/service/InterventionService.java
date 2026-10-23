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
        var intervention = warnings.appendAction(warning, request.getActionText(), request.getNotes(), request.getFollowUpDate());
        warnings.audit(warning, "INTERVENTION_APPENDED", "Intervention " + intervention.getInterventionId()
                + ": " + request.getNotes(), warning.getWarningStatus(), warning.getWarningStatus());
        return ResponseMapper.toResponse(intervention);
    }

    @PreAuthorize("hasAnyRole('RM', 'RISK_ADMIN')")
    public InterventionResponse updateIntervention(Long id, UpdateInterventionRequest request) {
        var warning = warnings.writable(warningId(id));
        requireActive(intervention(id));
        var intervention = warnings.appendAction(warning, request.getActionText(), request.getNotes(), request.getFollowUpDate());
        if (!id.equals(intervention.getInterventionId())) throw new InvalidRequestException("Intervention mismatch");
        warnings.audit(warning, "INTERVENTION_APPENDED", "Intervention " + id + ": " + request.getNotes(),
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
        warnings.appendAction(warning, !keepActive ? "close" : "ESCALATED".equals(request.getOutcome()) ? "escalate" : "intervene",
                request.getReviewNotes(), keepActive ? request.getNextFollowUpDate() : LocalDate.now());
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

}
