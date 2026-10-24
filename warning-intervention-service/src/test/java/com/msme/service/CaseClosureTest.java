package com.msme.service;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.msme.entity.*;
import com.msme.dto.warning.DecisionRequest;
import com.msme.repository.*;
import com.msme.security.CustomUserPrincipal;
class CaseClosureTest {
    private final WarningRepository warnings=mock(WarningRepository.class);
    private final RiskAssessmentRepository assessments=mock(RiskAssessmentRepository.class);
    private final InterventionRepository interventions=mock(InterventionRepository.class);
    private final CaseAccessService access=mock(CaseAccessService.class);
    private final Warning warning=new Warning();
    private final Intervention intervention=new Intervention();
    private final CustomUserPrincipal actor=mock(CustomUserPrincipal.class);
    private final WarningService service=new WarningService(warnings,assessments,mock(AssessmentIndicatorResultRepository.class),
            interventions,mock(ActivityLogRepository.class),access,mock(org.springframework.context.ApplicationEventPublisher.class));
    @BeforeEach void setup() {
        warning.setWarningId(1L);warning.setAssessmentId(2L);warning.setWarningStatus("OPEN");warning.setRecommendedAction("saved-json");
        intervention.setInterventionId(3L);intervention.setWarningId(1L);intervention.setIsActive("Y");intervention.setActionText("intervene");intervention.setNotes("Historical note");
        when(interventions.findByWarningIdOrderByInterventionIdAsc(1L)).thenReturn(List.of(intervention));
        when(interventions.saveAndFlush(any())).thenAnswer(call->call.getArgument(0));
        when(warnings.findLockedById(1L)).thenReturn(Optional.of(warning));
        var assessment=new RiskAssessment();assessment.setAssessmentId(2L);assessment.setMsmeId(10L);
        when(assessments.findById(2L)).thenReturn(Optional.of(assessment));
        when(access.currentUser()).thenReturn(actor);when(actor.getRole()).thenReturn("RM");when(actor.getUserId()).thenReturn(7L);
    }
    @Test void appendCloseClosesBothAndPreservesRecommendationAndNotes() {
        service.appendAction(warning,"close","Resolved",LocalDate.now());
        assertEquals("CLOSED",warning.getWarningStatus());assertEquals("N",intervention.getIsActive());
        assertEquals("saved-json",warning.getRecommendedAction());assertTrue(intervention.getNotes().startsWith("Historical note\n"));
        assertTrue(intervention.getNotes().contains("Resolved"));assertEquals(warning.getClosedAt(),intervention.getClosedAt());
    }
    @Test void warningDecisionCloseClosesBoth() {
        var request=new DecisionRequest();request.setDecision("CLOSE");request.setReason("Resolved");request.setFollowUpDate(LocalDate.now());
        service.decide(1L,request);assertEquals("CLOSED",warning.getWarningStatus());assertEquals("N",intervention.getIsActive());
    }
    @Test void adminCanAdviseOnEscalationButCannotClose() {
        when(actor.getRole()).thenReturn("RISK_ADMIN");intervention.setActionText("escalate");
        service.appendAction(warning,null,"Advice",LocalDate.now());
        assertEquals("escalate",intervention.getActionText());assertEquals("OPEN",warning.getWarningStatus());
        assertThrows(com.msme.exception.ForbiddenOperationException.class,()->service.appendAction(warning,"close","Close",LocalDate.now()));
    }
    @Test void pastDatesAndMultipleLegacyInterventionsRemainRejected() {
        assertThrows(com.msme.exception.InvalidRequestException.class,()->service.appendAction(warning,"intervene","Note",LocalDate.now().minusDays(1)));
        when(interventions.findByWarningIdOrderByInterventionIdAsc(1L)).thenReturn(List.of(intervention,new Intervention()));
        assertThrows(com.msme.exception.DuplicateResourceException.class,()->service.appendAction(warning,"intervene","Note",LocalDate.now()));
    }
    @Test void representativeDoesNotReceiveRecommendationField() {
        when(actor.getRole()).thenReturn("MSME_REP");when(warnings.findById(1L)).thenReturn(Optional.of(warning));
        assertNull(service.getWarning(1L).getRecommendedAction());assertEquals("saved-json",warning.getRecommendedAction());
    }
}
