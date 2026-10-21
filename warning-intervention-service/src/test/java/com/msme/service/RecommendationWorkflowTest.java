package com.msme.service;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import com.msme.entity.*;
import com.msme.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.Page;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecommendationWorkflowTest {
    private final WarningRepository warnings=mock(WarningRepository.class);
    private final InterventionRepository interventions=mock(InterventionRepository.class);
    private final FinancialSnapshotRepository snapshots=mock(FinancialSnapshotRepository.class);
    private final ActivityLogRepository activities=mock(ActivityLogRepository.class);
    private final WarningService cases=mock(WarningService.class);
    private final Warning warning=new Warning();
    private final AtomicInteger requests=new AtomicInteger();
    private HttpServer server;
    private RecommendationWorkflow workflow;
    private String reply="{\"status\":\"generated\",\"recommendation\":{\"actions\":[{\"title\":\"Collections\",\"suggested_action\":\"Call overdue customers\"},{\"title\":\"Credit\",\"suggested_action\":\"Review utilisation\"}]},\"evidence\":{\"source\":\"saved assessment\"}}";
    private int status=200;
    @BeforeEach void setup() throws Exception {
        warning.setWarningId(10L);warning.setAssessmentId(20L);warning.setWarningStatus("OPEN");warning.setRecommendedAction(RecommendationWorkflow.UNAVAILABLE);
        var assessment=new RiskAssessment();assessment.setAssessmentId(20L);assessment.setMsmeId(1L);assessment.setSnapshotId(30L);
        var snapshot=new FinancialSnapshot();snapshot.setReportingPeriod(LocalDate.of(2026,9,1));
        when(warnings.findLockedById(10L)).thenReturn(Optional.of(warning));
        when(cases.assessment(20L)).thenReturn(assessment);
        when(cases.writable(10L)).thenReturn(warning);
        when(snapshots.findById(30L)).thenReturn(Optional.of(snapshot));
        when(interventions.findByWarningId(eq(10L),any())).thenReturn(Page.empty());
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/recommendations", exchange->{
            requests.incrementAndGet();
            assertEquals("t".repeat(40),exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            assertTrue(body.contains("2026-09"));assertTrue(body.contains("msme_id"));
            byte[] bytes=reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        var manager=mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        doAnswer(inv->{ if("RECOMMENDATION_GENERATED".equals(inv.getArgument(1)))
            when(activities.existsByRelatedEntityTypeAndRelatedEntityIdAndActionCode("WARNING",10L,"RECOMMENDATION_GENERATED")).thenReturn(true);
            return null; }).when(cases).systemAudit(any(),anyString(),anyString(),anyString());
        workflow=new RecommendationWorkflow(warnings,interventions,snapshots,activities,cases,
            "http://127.0.0.1:"+server.getAddress().getPort(),"t".repeat(40), manager);
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void successfulActionsAreCombinedEvidenceSavedAndNeverRegenerated() {
        workflow.generate(10L);
        assertTrue(warning.getRecommendedAction().contains("1. Collections: Call overdue customers"));
        assertTrue(warning.getRecommendedAction().contains("2. Credit: Review utilisation"));
        verify(cases).systemAudit(eq(warning),eq("RECOMMENDATION_GENERATED"),anyString(),contains("saved assessment"));
        workflow.generate(10L);
        assertEquals(1,requests.get());
    }
    @Test void failedGenerationCanBeExplicitlyRetriedByOwner() {
        status=503;workflow.generate(10L);
        assertEquals(RecommendationWorkflow.UNAVAILABLE,warning.getRecommendedAction());
        verify(cases).systemAudit(eq(warning),eq("RECOMMENDATION_UNAVAILABLE"),anyString(),anyString());
        status=200;workflow.explicitlyGenerate(10L);
        verify(cases, times(2)).requireOwner(warning);
        assertTrue(workflow.generated(10L));
        assertEquals(2,requests.get());
    }
    @Test void emptyGeneratedActionsRemainUnavailable() {
        reply="{\"status\":\"generated\",\"recommendation\":{\"actions\":[]}}";
        workflow.generate(10L);
        assertEquals(RecommendationWorkflow.UNAVAILABLE,warning.getRecommendedAction());
    }

    @Test void closedCaseNeverCallsTheModel() {
        warning.setWarningStatus("CLOSED");workflow.generate(10L);assertEquals(0,requests.get());
        when(cases.writable(10L)).thenThrow(new com.msme.exception.DuplicateResourceException("Closed case"));
        assertThrows(com.msme.exception.DuplicateResourceException.class,()->workflow.explicitlyGenerate(10L));
    }
    @Test void missingActionsAreFilledWithoutReplacingHistoricalInterventionText() {
        var missing=new Intervention();missing.setActionText(RecommendationWorkflow.UNAVAILABLE);
        var historical=new Intervention();historical.setActionText("Existing recorded action");
        when(interventions.findByWarningId(eq(10L),any())).thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(missing,historical)));
        workflow.generate(10L);
        assertTrue(missing.getActionText().contains("Call overdue customers"));
        assertEquals("Existing recorded action",historical.getActionText());
    }
}
