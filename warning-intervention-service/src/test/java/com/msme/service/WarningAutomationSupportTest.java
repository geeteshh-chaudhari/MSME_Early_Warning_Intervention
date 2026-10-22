package com.msme.service;

import java.util.List;
import java.util.Optional;
import com.msme.entity.*;
import com.msme.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WarningAutomationSupportTest {
    private final WarningRepository warnings = mock(WarningRepository.class);
    private final RiskAssessmentRepository assessments = mock(RiskAssessmentRepository.class);
    private final AssessmentIndicatorResultRepository indicators = mock(AssessmentIndicatorResultRepository.class);
    private final ActivityLogRepository activities = mock(ActivityLogRepository.class);
    private final CaseAccessService access = mock(CaseAccessService.class);
    private final WarningService service = new WarningService(warnings, assessments, indicators,
            mock(InterventionRepository.class), activities, access);

    private RiskAssessment assessment(String severity) {
        var assessment = new RiskAssessment();
        assessment.setAssessmentId(7L);
        assessment.setMsmeId(9L);
        assessment.setSeverity(severity);
        when(assessments.findById(7L)).thenReturn(Optional.of(assessment));
        return assessment;
    }

    @Test void automaticCreationNeedsNoHumanIdentityAndRecordsSystemAudit() {
        assessment("HIGH_PRIORITY");
        var indicator = new AssessmentIndicatorResult();
        indicator.setIndicatorCode("REVENUE_DETERIORATION");
        indicator.setBreachedFlag("Y");
        indicator.setExplanationText("Current revenue is zero; percentage is unavailable.");
        when(indicators.findByAssessmentIdOrderByIndicatorCode(7L)).thenReturn(List.of(indicator));
        when(warnings.saveAndFlush(any(Warning.class))).thenAnswer(call -> {
            Warning warning = call.getArgument(0);
            warning.setWarningId(11L);
            return warning;
        });

        var result = service.createAutomatic(7L);

        assertEquals("{\"status\":\"pending\",\"actions\":[]}", result.getRecommendedAction());
        assertTrue(result.getWhyNowText().contains("Current revenue is zero"));
        var audit = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activities).save(audit.capture());
        assertEquals("SYSTEM", audit.getValue().getActorType());
        assertNull(audit.getValue().getActorUserId());
        assertEquals(9L, audit.getValue().getMsmeId());
        verifyNoInteractions(access);
    }

    @Test void repeatedAutomaticCreationReturnsExistingWarning() {
        assessment("WATCH");
        var warning = new Warning();
        warning.setWarningId(11L);
        warning.setAssessmentId(7L);
        when(warnings.findByAssessmentId(7L)).thenReturn(Optional.of(warning));

        assertEquals(11L, service.createAutomatic(7L).getWarningId());
        verify(warnings, never()).saveAndFlush(any());
        verifyNoInteractions(activities, indicators, access);
    }

    @Test void healthyAssessmentCreatesNoWarningOrAudit() {
        assessment("HEALTHY");
        assertNull(service.createAutomatic(7L));
        verifyNoInteractions(warnings, indicators, activities, access);
    }

    @Test void escalationHistoryKeepsAdminOwnershipAfterStatusChanges() {
        var warning = new Warning();
        warning.setWarningId(11L);
        warning.setWarningStatus("INTERVENTION_ACTIVE");
        when(warnings.findById(11L)).thenReturn(Optional.of(warning));
        when(activities.existsByRelatedEntityTypeAndRelatedEntityIdAndActionCode(
                "WARNING", 11L, "ESCALATE")).thenReturn(true);

        service.requireOwner(warning);

        verify(access).requireRole("RISK_ADMIN");
        verify(access, never()).requireRole("RM");
    }

    @Test void ordinaryCaseRequiresRmOwnership() {
        var warning = new Warning();
        warning.setWarningId(11L);
        warning.setWarningStatus("OPEN");
        when(warnings.findById(11L)).thenReturn(Optional.of(warning));

        service.requireOwner(warning);

        verify(access).requireRole("RM");
    }

    @Test void generatedEvidenceIsStoredWithoutReplacingItsJson() {
        assessment("WATCH");
        var warning = new Warning();
        warning.setWarningId(11L);
        warning.setAssessmentId(7L);
        String evidence = "{\"recommendation\":{\"actions\":[]},\"evidence\":{}}";

        service.systemAudit(warning, "RECOMMENDATION_GENERATED", "Saved", evidence);

        var audit = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activities).save(audit.capture());
        assertEquals(evidence, audit.getValue().getMetadataJson());
        assertEquals("WARNING", audit.getValue().getRelatedEntityType());
        assertEquals(11L, audit.getValue().getRelatedEntityId());
        verifyNoInteractions(access);
    }
}
