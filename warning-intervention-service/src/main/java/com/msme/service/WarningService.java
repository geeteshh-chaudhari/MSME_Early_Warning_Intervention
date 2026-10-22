package com.msme.service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.msme.dto.warning.*;
import com.msme.entity.*;
import com.msme.exception.*;
import com.msme.repository.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WarningService {
    private static final Set<String> STATUSES = Set.of("OPEN", "UNDER_REVIEW", "ESCALATED",
            "INTERVENTION_ACTIVE", "CLOSED_REVIEWED", "CLOSED");
    private final WarningRepository warnings;
    private final RiskAssessmentRepository assessments;
    private final AssessmentIndicatorResultRepository indicators;
    private final InterventionRepository interventions;
    private final ActivityLogRepository activities;
    private final CaseAccessService access;

    public WarningService(WarningRepository warnings, RiskAssessmentRepository assessments,
            AssessmentIndicatorResultRepository indicators, InterventionRepository interventions,
            ActivityLogRepository activities, CaseAccessService access) {
        this.warnings = warnings; this.assessments = assessments; this.indicators = indicators;
        this.interventions = interventions; this.activities = activities; this.access = access;
    }

    @PreAuthorize("hasAnyRole('RM', 'RISK_ADMIN')")
    public WarningResponse createWarning(CreateWarningRequest request) {
        var assessment = assessment(request.getAssessmentId());
        access.authorize(assessment.getMsmeId(), true);
        if ("HEALTHY".equals(assessment.getSeverity())) {
            throw new InvalidRequestException("A healthy assessment cannot create a warning");
        }
        if (warnings.findByAssessmentId(assessment.getAssessmentId()).isPresent()) {
            throw new DuplicateResourceException("A warning already exists for this assessment");
        }
        return createFromEvidence(assessment, false);
    }

    /** Called by reconciliation while it holds the assessment row lock. */
    public WarningResponse createAutomatic(Long assessmentId) {
        var assessment = assessment(assessmentId);
        if ("HEALTHY".equals(assessment.getSeverity())) return null;
        var existing = warnings.findByAssessmentId(assessmentId);
        if (existing.isPresent()) return ResponseMapper.toResponse(existing.get());
        return createFromEvidence(assessment, true);
    }

    private WarningResponse createFromEvidence(RiskAssessment assessment, boolean automatic) {
        var breaches = indicators.findByAssessmentIdOrderByIndicatorCode(assessment.getAssessmentId())
                .stream().filter(i -> "Y".equals(i.getBreachedFlag())).toList();
        if (breaches.isEmpty()) throw new InvalidRequestException("Assessment has no breached indicators");
        var warning = new Warning();
        warning.setAssessmentId(assessment.getAssessmentId());
        warning.setWarningStatus("OPEN");
        // Deterministic priority is documented; this service does not recalculate risk.
        warning.setPrimaryDriver(breaches.getFirst().getIndicatorCode());
        warning.setWhyNowText("Assessment " + assessment.getAssessmentId() + " ("
                + assessment.getSeverity() + "): " + breaches.stream().map(i ->
                    i.getIndicatorCode() + " = " + i.getCalculatedValue() + ", threshold = "
                    + i.getThresholdValueUsed() + ". "
                    + Objects.toString(i.getExplanationText(), "")).collect(Collectors.joining("; ")));
        warning.setRecommendedAction(automatic ? RecommendationWorkflow.UNAVAILABLE : switch (warning.getPrimaryDriver()) {
            case "REVENUE_DETERIORATION" -> "Review revenue decline and agree a cash-flow recovery plan.";
            case "OVERDUE_RECEIVABLES" -> "Review overdue invoices and agree a collections plan.";
            default -> "Review credit utilisation and agree a working-capital plan.";
        });
        warning.setCreatedAt(LocalDateTime.now());
        warnings.saveAndFlush(warning);
        if (automatic) {
            systemAudit(warning, "WARNING_CREATED", "Warning created from stored assessment evidence",
                    "{\"previousStatus\":null,\"nextStatus\":\"OPEN\",\"assessmentId\":"
                            + assessment.getAssessmentId() + "}");
        } else {
            audit(warning, "WARNING_CREATED", "Warning created from stored assessment evidence", null, "OPEN");
        }
        return ResponseMapper.toResponse(warning);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('RM', 'RISK_ADMIN', 'MSME_REP')")
    public Page<WarningResponse> getWarnings(Long msmeId, String status, int page, int size) {
        access.authorize(msmeId, false);
        if (status != null && !STATUSES.contains(status)) throw new InvalidRequestException("Unknown warning status");
        return warnings.findForMsme(msmeId, status, page(page, size, "warningId"))
                .map(ResponseMapper::toResponse);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('RM', 'RISK_ADMIN', 'MSME_REP')")
    public WarningResponse getWarning(Long id) { return ResponseMapper.toResponse(readable(id)); }

    @PreAuthorize("hasAnyRole('RM', 'RISK_ADMIN')")
    public WarningResponse decide(Long id, DecisionRequest request) {
        var warning = writable(id);
        String decision = request.getDecision();
        String prior = warning.getWarningStatus();
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new InvalidRequestException("Decision reason is required");
        }
        if ("CLOSE".equals(decision) && "RM".equals(access.currentUser().getRole())) {
            requireState(warning, "INTERVENTION_ACTIVE");
            warning.setWarningStatus("CLOSED");
        } else if (Set.of("ACCEPT", "REJECT", "ESCALATE").contains(decision)) {
            access.requireRole("RM");
            requireState(warning, "OPEN", "UNDER_REVIEW", "INTERVENTION_ACTIVE");
            if ("INTERVENTION_ACTIVE".equals(prior) && !"ESCALATE".equals(decision)) {
                throw new DuplicateResourceException("An active intervention can only be escalated, closed or followed up");
            }
            warning.setWarningStatus(switch (decision) {
                case "ACCEPT" -> "UNDER_REVIEW";
                case "REJECT" -> "CLOSED_REVIEWED";
                default -> "ESCALATED";
            });
        } else {
            access.requireRole("RISK_ADMIN");
            requireState(warning, "ESCALATED");
            warning.setWarningStatus(switch (decision) {
                case "RETURN", "CONFIRM" -> interventions.findByWarningIdAndIsActive(id, "Y").isEmpty()
                        ? "UNDER_REVIEW" : "INTERVENTION_ACTIVE";
                case "CLOSE" -> "CLOSED";
                default -> throw new InvalidRequestException("Unknown decision");
            });
        }
        if (isClosed(warning)) closeInterventions(warning);
        audit(warning, decision, request.getReason().trim(), prior, warning.getWarningStatus());
        return ResponseMapper.toResponse(warning);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('RM', 'RISK_ADMIN')")
    public Page<ActivityLogResponse> getHistory(Long id, int page, int size) {
        readable(id);
        return activities.findByRelatedEntityTypeAndRelatedEntityId("WARNING", id,
                page(page, size, "activityId")).map(ResponseMapper::toResponse);
    }

    @PreAuthorize("hasAnyRole('RM', 'RISK_ADMIN')")
    public ActivityLogResponse addNote(Long id, CreateWarningNoteRequest request) {
        var warning = writable(id);
        String notes = request.getNotes();
        if (notes == null || notes.isBlank() || notes.length() > 4000) {
            throw new InvalidRequestException("Notes must contain between 1 and 4000 characters");
        }
        String prefix = "RISK_ADMIN".equals(access.currentUser().getRole()) ? "Admin: " : "Rm: ";
        String note = prefix + notes.trim();
        String existingAction = warning.getRecommendedAction();
        warning.setRecommendedAction(existingAction == null || existingAction.isBlank()
                ? note : existingAction + ",\n" + note);
        return ResponseMapper.toResponse(audit(warning, "NOTE_ADDED", note,
                warning.getWarningStatus(), warning.getWarningStatus()));
    }

    Warning readable(Long id) {
        var warning = warnings.findById(id).orElseThrow(() -> new ResourceNotFoundException("Warning not found"));
        access.authorize(assessment(warning.getAssessmentId()).getMsmeId(), false);
        return warning;
    }

    Warning writable(Long id) {
        var warning = warnings.findLockedById(id).orElseThrow(() -> new ResourceNotFoundException("Warning not found"));
        access.authorize(assessment(warning.getAssessmentId()).getMsmeId(), true);
        if (isClosed(warning)) throw new DuplicateResourceException("Closed cases cannot be changed");
        return warning;
    }

    RiskAssessment assessment(Long id) {
        return assessments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Assessment not found"));
    }

    static boolean isClosed(Warning w) { return Set.of("CLOSED", "CLOSED_REVIEWED").contains(w.getWarningStatus()); }

    public boolean isEscalated(Long id) {
        var warning = warnings.findById(id).orElseThrow(() -> new ResourceNotFoundException("Warning not found"));
        return "ESCALATED".equals(warning.getWarningStatus())
                || activities.existsByRelatedEntityTypeAndRelatedEntityIdAndActionCode("WARNING", id, "ESCALATE")
                || activities.existsByRelatedEntityTypeAndRelatedEntityIdAndActionCode("WARNING", id, "FOLLOW_UP_ESCALATED");
    }

    @Transactional(readOnly = true)
    public boolean adminNotesAvailable(Long id) {
        var warning = readable(id);
        if (isClosed(warning)) return false;
        if ("ESCALATED".equals(warning.getWarningStatus())) return true;
        return activities.findFirstByRelatedEntityTypeAndRelatedEntityIdAndActionCodeInOrderByActivityIdDesc(
                "WARNING", id, Set.of("ESCALATE", "FOLLOW_UP_ESCALATED", "CONFIRM", "RETURN"))
                .map(activity -> "CONFIRM".equals(activity.getActionCode())).orElse(false);
    }

    void requireOwner(Warning warning) {
        access.requireRole(isEscalated(warning.getWarningId()) ? "RISK_ADMIN" : "RM");
    }

    public void systemAudit(Warning warning, String action, String description, String metadata) {
        var entry = new ActivityLog();
        entry.setMsmeId(assessment(warning.getAssessmentId()).getMsmeId());
        entry.setActorType("SYSTEM");
        entry.setActionCode(action);
        entry.setRelatedEntityType("WARNING");
        entry.setRelatedEntityId(warning.getWarningId());
        entry.setDescription(description);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setMetadataJson(metadata == null ? "{}" : metadata);
        activities.save(entry);
    }

    static void requireState(Warning w, String... allowed) {
        if (!Arrays.asList(allowed).contains(w.getWarningStatus())) {
            throw new DuplicateResourceException("Action is not allowed in status " + w.getWarningStatus());
        }
    }

    static PageRequest page(int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) throw new InvalidRequestException("Page must be >= 0 and size between 1 and 100");
        return PageRequest.of(page, size, Sort.by(sort).descending());
    }

    void closeInterventions(Warning warning) {
        var now = LocalDateTime.now();
        warning.setClosedAt(now);
        for (var intervention : interventions.findByWarningIdAndIsActive(warning.getWarningId(), "Y")) {
            intervention.setIsActive("N");
            intervention.setClosedAt(now);
            intervention.setClosedByUserId(access.currentUser().getUserId());
        }
    }

    ActivityLog audit(Warning warning, String action, String reason, String prior, String next) {
        var entry = new ActivityLog();
        entry.setMsmeId(assessment(warning.getAssessmentId()).getMsmeId());
        entry.setActorUserId(access.currentUser().getUserId());
        entry.setActorType("USER");
        entry.setActionCode(action);
        entry.setRelatedEntityType("WARNING");
        entry.setRelatedEntityId(warning.getWarningId());
        entry.setDescription(reason);
        entry.setCreatedAt(LocalDateTime.now());
        // Only internally defined statuses and numeric IDs are interpolated into JSON.
        entry.setMetadataJson("{\"previousStatus\":" + (prior == null ? "null" : "\"" + prior + "\"")
                + ",\"nextStatus\":\"" + next + "\",\"assessmentId\":" + warning.getAssessmentId() + "}");
        return activities.save(entry);
    }
}
