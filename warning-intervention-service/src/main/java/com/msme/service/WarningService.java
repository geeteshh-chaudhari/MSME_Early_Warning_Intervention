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
        warning.setRecommendedAction("{\"status\":\"pending\",\"actions\":[]}");
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
        if ("RISK_ADMIN".equals(access.currentUser().getRole())) {
            if (!Set.of("RETURN", "CONFIRM").contains(decision))
                throw new ForbiddenOperationException("Risk Admin can only append advice for the RM");
            appendAction(warning, null, request.getReason(), request.getFollowUpDate());
        } else {
            access.requireRole("RM");
            if (!Set.of("INTERVENE", "ESCALATE", "CLOSE").contains(decision))
                throw new InvalidRequestException("Choose INTERVENE, ESCALATE or CLOSE");
            appendAction(warning, decision.toLowerCase(Locale.ROOT), request.getReason(), request.getFollowUpDate(), "CLOSE".equals(decision));
            // Intervention closure alone never changes warning status.
            if ("CLOSE".equals(decision)) {
                warning.setWarningStatus("CLOSED");
                warning.setClosedAt(LocalDateTime.now());
            }
        }
        audit(warning, decision, request.getReason().trim(), prior, warning.getWarningStatus());
        return ResponseMapper.toResponse(warning);
    }

    /** Caller holds the warning row lock, serializing appends and initial creation. */
    Intervention appendAction(Warning warning, String action, String note, java.time.LocalDate date) {
        return appendAction(warning, action, note, date, false);
    }

    private Intervention appendAction(Warning warning, String action, String note, java.time.LocalDate date,
            boolean explicitWarningClosure) {
        if (note == null || note.isBlank() || note.length() > 4000)
            throw new InvalidRequestException("Notes must contain between 1 and 4000 characters");
        if (date == null || date.isBefore(java.time.LocalDate.now()))
            throw new InvalidRequestException("Follow-up date must be today or later");
        var records = interventions.findByWarningIdOrderByInterventionIdAsc(warning.getWarningId());
        if (records.size() > 1)
            throw new DuplicateResourceException("This legacy warning has multiple interventions; consolidate its history before appending");
        var intervention = records.isEmpty() ? new Intervention() : records.getFirst();
        var actor = access.currentUser();
        boolean admin = "RISK_ADMIN".equals(actor.getRole());
        if (admin) {
            if (records.isEmpty() || !"escalate".equals(intervention.getActionText()))
                throw new ForbiddenOperationException("Risk Admin notes are editable only after RM escalation");
            if (action != null) throw new ForbiddenOperationException("Risk Admin can append advice only");
        } else {
            access.requireRole("RM");
            if (action == null && !records.isEmpty()) action = intervention.getActionText();
            if (action == null || !Set.of("intervene", "escalate", "close").contains(action))
                throw new InvalidRequestException("Choose intervene, escalate or close");
        }
        if (!records.isEmpty() && !"Y".equals(intervention.getIsActive()) && !explicitWarningClosure)
            throw new DuplicateResourceException("Intervention is closed; further actions are disabled");
        if (records.isEmpty()) {
            intervention.setWarningId(warning.getWarningId());
            intervention.setCreatedByUserId(actor.getUserId());
            intervention.setCreatedAt(LocalDateTime.now());
            intervention.setIsActive("Y");
        }
        String entry = actor.getRole() + " #" + actor.getUserId() + " "
                + (admin ? "advice" : action) + " | " + note.trim() + " | " + date;
        String previous = intervention.getNotes();
        intervention.setNotes(previous == null || previous.isBlank() ? entry : previous + "\n" + entry);
        intervention.setFollowUpDate(date);
        if (!admin) intervention.setActionText(action);
        if ("close".equals(action)) {
            intervention.setIsActive("N");
            intervention.setClosedAt(LocalDateTime.now());
            intervention.setClosedByUserId(actor.getUserId());
        }
        return interventions.saveAndFlush(intervention);
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
        throw new InvalidRequestException("Append notes to the intervention with a follow-up date; recommendations contain LLM JSON only");
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
        return interventions.findByWarningIdOrderByInterventionIdAsc(id).stream()
                .anyMatch(i -> "Y".equals(i.getIsActive()) && "escalate".equals(i.getActionText()));
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

    static PageRequest page(int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) throw new InvalidRequestException("Page must be >= 0 and size between 1 and 100");
        return PageRequest.of(page, size, Sort.by(sort).descending());
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
