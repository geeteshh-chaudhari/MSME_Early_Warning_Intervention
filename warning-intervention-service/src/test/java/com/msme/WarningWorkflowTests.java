package com.msme;

import java.time.*;
import java.sql.Timestamp;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.msme.dto.warning.*;
import com.msme.dto.intervention.*;
import com.msme.dto.followup.*;
import com.msme.entity.*;
import com.msme.exception.*;
import com.msme.repository.ActivityLogRepository;
import com.msme.security.*;
import com.msme.service.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:warning;MODE=Oracle;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "workflow.enabled=false",
    "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
    "app.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
    "spring.cloud.compatibility-verifier.enabled=true"
})
class WarningWorkflowTests {
    @Autowired WarningService warnings;
    @Autowired InterventionService interventions;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired WebApplicationContext webContext;
    @Autowired com.msme.controller.RecommendationController recommendations;
    @MockitoSpyBean ActivityLogRepository activities;
    private final LocalDate month = LocalDate.now().withDayOfMonth(1);

    @BeforeEach void setup() {
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        reset(activities);
        for (String table : new String[]{"activity_log", "follow_up_review", "intervention", "warning",
                "assessment_indicator_result", "risk_assessment", "financial_snapshot", "msme", "app_user"}) {
            jdbc.update("delete from " + table);
        }
        jdbc.update("insert into app_user(user_id,name,email,password_hash,role,account_status,created_at,updated_at) values(1,'Manager','rm@test','hash','RM','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("insert into msme(msme_id,business_name,sector,contact_name,sanctioned_credit_limit,assigned_rm_id,is_active,created_at,updated_at) values(1,'Business','Trade','Contact',10000,1,'Y',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        assessment(1, 1, month.minusMonths(2), "HIGH_PRIORITY");
        assessment(2, 1, month.minusMonths(1), "WATCH");
        assessment(3, 1, month, "HEALTHY");
        jdbc.update("insert into assessment_indicator_result(indicator_result_id,assessment_id,indicator_code,calculated_value,threshold_value_used,breached_flag,explanation_text) values(1,1,'REVENUE_DETERIORATION',30,15,'Y','Revenue fell from the prior period')");
        login("RM", 1L, null);
    }

    @AfterEach void clear() { org.springframework.security.test.context.TestSecurityContextHolder.clearContext(); }


    @Test void warningEvidenceAndAutomaticDeduplication() {
        var result = warnings.createAutomatic(1L);
        assertEquals("OPEN", result.getWarningStatus());
        assertTrue(result.getWhyNowText().contains("Revenue fell"));
        assertTrue(result.getRecommendedAction().contains("\"pending\""));
        assertEquals(result.getWarningId(), warnings.createAutomatic(1L).getWarningId());
        assertEquals(1L, count("warning"));
    }

    @Test void healthyAssessmentCannotWarn() {
        assertNull(warnings.createAutomatic(3L));
        assertThrows(InvalidRequestException.class, () -> warnings.createWarning(new CreateWarningRequest(3L)));
    }

    @Test void onlyAssignedRmCanAccessRecommendations() {
        long id = create();
        String advice = "{\"summary\":\"Review collections\",\"actions\":[]}";
        jdbc.update("update warning set recommended_action=? where warning_id=?", advice, id);
        assertEquals(advice, recommendations.state(id).get("actionText"));
        for (String role : new String[]{"RISK_ADMIN", "MSME_REP"}) {
            login(role, 7L, 1L);
            assertNull(warnings.getWarning(id).getRecommendedAction());
            assertNull(warnings.getWarnings(1L, null, 0, 10).getContent().getFirst().getRecommendedAction());
            assertThrows(AccessDeniedException.class, () -> recommendations.state(id));
            assertThrows(AccessDeniedException.class, () -> recommendations.generate(id));
        }
        login("RM", 99L, null);
        assertThrows(ForbiddenOperationException.class, () -> recommendations.state(id));
    }

    @Test void repeatedRmActionsAppendToOneInterventionAndPreserveJson() {
        long id = create();
        var originalJson = warnings.getWarning(id).getRecommendedAction();
        decide(id, "INTERVENE", "Call the customer");
        long intervention = interventionId(id);
        decide(id, "ESCALATE", "Review collections risk");
        decide(id, "INTERVENE", "Agree payment plan");
        assertEquals(1L, count("intervention"));
        assertEquals(intervention, interventionId(id));
        assertEquals("intervene", text("action_text", intervention));
        String notes = text("notes", intervention);
        assertTrue(notes.contains("Call the customer"));
        assertTrue(notes.contains("Review collections risk"));
        assertTrue(notes.contains("Agree payment plan"));
        assertTrue(notes.contains("RM #1"));
        assertTrue(notes.contains(LocalDate.now().plusDays(7).toString()));
        assertEquals(originalJson, warnings.getWarning(id).getRecommendedAction());
        assertEquals("OPEN", warnings.getWarning(id).getWarningStatus());
    }

    @Test void appendUpdatesLatestDateWithoutReplacingNotes() {
        long id = create();
        decide(id, "INTERVENE", "Original note");
        long intervention = interventionId(id);
        interventions.updateIntervention(intervention, new UpdateInterventionRequest("Next conversation", LocalDate.now().plusDays(20)));
        assertTrue(text("notes", intervention).contains("Original note"));
        assertTrue(text("notes", intervention).contains("Next conversation"));
        assertEquals(LocalDate.now().plusDays(20), jdbc.queryForObject("select follow_up_date from intervention where intervention_id=?", LocalDate.class, intervention));
        assertEquals(1L, count("intervention"));
    }

    @Test void riskAdminCanAppendAdviceOnlyWhileEscalated() {
        long id = create();
        decide(id, "INTERVENE", "RM starts work");
        long intervention = interventionId(id);
        login("RISK_ADMIN", 7L, null);
        assertThrows(ForbiddenOperationException.class, () -> interventions.updateIntervention(intervention,
                new UpdateInterventionRequest("Too early", LocalDate.now().plusDays(7))));
        login("RM", 1L, null);
        decide(id, "ESCALATE", "Please advise");
        login("RISK_ADMIN", 7L, null);
        interventions.updateIntervention(intervention, new UpdateInterventionRequest("RM should contact accounts", LocalDate.now().plusDays(10)));
        assertTrue(text("notes", intervention).contains("RISK_ADMIN #7 advice"));
        assertEquals("escalate", text("action_text", intervention));
        assertThrows(ForbiddenOperationException.class, () -> decide(id, "INTERVENE", "Admin cannot intervene"));
        assertThrows(ForbiddenOperationException.class, () -> decide(id, "CLOSE", "Admin cannot close"));
        var forbidden = new UpdateInterventionRequest("Cannot close", LocalDate.now().plusDays(10));
        forbidden.setActionText("close");
        assertThrows(ForbiddenOperationException.class, () -> interventions.updateIntervention(intervention, forbidden));
        login("RM", 1L, null);
        decide(id, "INTERVENE", "RM acts on admin advice");
        login("RISK_ADMIN", 7L, null);
        assertThrows(ForbiddenOperationException.class, () -> interventions.updateIntervention(intervention,
                new UpdateInterventionRequest("Escalation ended", LocalDate.now().plusDays(10))));
    }

    @Test void interventionClosureDoesNotCloseWarningAndRmCanThenCloseWarningExplicitly() {
        long id = create();
        decide(id, "INTERVENE", "Contact customer");
        long intervention = interventionId(id);
        var close = new UpdateInterventionRequest("Intervention work finished", LocalDate.now().plusDays(1));
        close.setActionText("close");
        interventions.updateIntervention(intervention, close);
        assertEquals("N", text("is_active", intervention));
        assertEquals("OPEN", warnings.getWarning(id).getWarningStatus());
        assertThrows(DuplicateResourceException.class, () -> decide(id, "INTERVENE", "Cannot reopen"));
        decide(id, "CLOSE", "RM explicitly closes warning");
        assertEquals("CLOSED", warnings.getWarning(id).getWarningStatus());
        assertTrue(text("notes", intervention).contains("RM explicitly closes warning"));
        assertEquals(1L, count("intervention"));
    }

    @Test void firstActionCanCloseWarningWithOneInterventionAndReason() {
        long id = create();
        decide(id, "CLOSE", "Data discrepancy explained");
        assertEquals("CLOSED", warnings.getWarning(id).getWarningStatus());
        assertEquals("close", text("action_text", interventionId(id)));
        assertTrue(text("notes", interventionId(id)).contains("Data discrepancy explained"));
        assertEquals(1L, count("intervention"));
    }

    @Test void closedInterventionCannotBeReplacedOrAppended() {
        long id = create();
        interventions.createIntervention(id, new CreateInterventionRequest("close", "Work completed", LocalDate.now()));
        assertThrows(DuplicateResourceException.class, () -> interventions.createIntervention(id,
                new CreateInterventionRequest("intervene", "New plan", LocalDate.now())));
        assertThrows(DuplicateResourceException.class, () -> interventions.updateIntervention(interventionId(id),
                new UpdateInterventionRequest("New note", LocalDate.now())));
        assertEquals(1L, count("intervention"));
    }

    @Test void legacyMultipleInterventionsArePreservedAndCannotReceiveAnotherRow() {
        long id = create();
        for (int index = 0; index < 2; index++) {
            jdbc.update("insert into intervention(warning_id,created_by_user_id,action_text,notes,is_active,follow_up_date,created_at) values(?,1,'Historical plan','Preserved note','Y',CURRENT_DATE,CURRENT_TIMESTAMP)", id);
        }
        assertThrows(DuplicateResourceException.class, () -> decide(id, "INTERVENE", "Cannot create a third"));
        assertEquals(2L, count("intervention"));
    }

    @Test void pastDatesAndBlankNotesAreRejectedWithoutCreatingInterventions() {
        long id = create();
        var past = new DecisionRequest("INTERVENE", "Contact");
        past.setFollowUpDate(LocalDate.now().minusDays(1));
        assertThrows(InvalidRequestException.class, () -> warnings.decide(id, past));
        assertThrows(InvalidRequestException.class, () -> decide(id, "INTERVENE", " "));
        assertEquals(0L, count("intervention"));
    }

    @Test void warningNotesEndpointCannotOverwriteRecommendations() {
        long id = create();
        String original = warnings.getWarning(id).getRecommendedAction();
        assertThrows(InvalidRequestException.class, () -> warnings.addNote(id, new CreateWarningNoteRequest("Human note")));
        assertEquals(original, warnings.getWarning(id).getRecommendedAction());
    }

    @Test void assignedRmAndRepresentativePermissions() {
        long id = create();
        login("RM", 2L, null);
        assertThrows(ForbiddenOperationException.class, () -> decide(id, "INTERVENE", "Wrong RM"));
        login("MSME_REP", 3L, 1L);
        assertEquals(id, warnings.getWarning(id).getWarningId());
        assertThrows(AccessDeniedException.class, () -> decide(id, "INTERVENE", "Rep cannot act"));
        login("MSME_REP", 4L, 2L);
        assertThrows(ForbiddenOperationException.class, () -> warnings.getWarning(id));
    }

    @Test void auditFailureRollsBackInterventionAndNotes() {
        long id = create();
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("test failure")).when(activities).save(any(ActivityLog.class));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> decide(id, "INTERVENE", "Should roll back"));
        assertEquals(0L, count("intervention"));
    }

    @Test void concurrentFirstActionsReuseOneIntervention() throws Exception {
        long id = create();
        var executor = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        Callable<Void> action = () -> {
            login("RM", 1L, null);
            gate.await();
            try { decide(id, "INTERVENE", Thread.currentThread().getName()); return null; }
            finally { SecurityContextHolder.clearContext(); }
        };
        try {
            var first = executor.submit(action); var second = executor.submit(action); gate.countDown();
            first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
            assertEquals(1L, count("intervention"));
            assertEquals(2, text("notes", interventionId(id)).lines().count());
        } finally { executor.shutdownNow(); }
    }

    @Test void followUpClosureLeavesWarningOpenAndLogsReview() {
        long id = create();
        decide(id, "INTERVENE", "Collections plan");
        long intervention = interventionId(id);
        jdbc.update("update intervention set created_at=? where intervention_id=?", Timestamp.valueOf(month.minusMonths(2).atStartOfDay()), intervention);
        interventions.followUp(intervention, new CreateFollowUpRequest(2L, "IMPROVING", "Collections improved", false, null));
        assertEquals("OPEN", warnings.getWarning(id).getWarningStatus());
        assertEquals("N", text("is_active", intervention));
        assertTrue(text("notes", intervention).contains("Collections improved"));
    }

    @Test void documentationIsPublicButBusinessApiStillRequiresJwt() throws Exception {
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        var mvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.servers[0].url").value("/"))
            .andExpect(jsonPath("$.security[0].bearerAuth").isArray())
            .andExpect(jsonPath("$.paths['/api/warnings'].post.responses['201']").exists());
        mvc.perform(get("/api/warnings").param("msmeId", "1")).andExpect(status().isUnauthorized());
    }

    @Test void assessmentWithoutBreachesCannotWarn() {
        assertThrows(InvalidRequestException.class, () -> warnings.createWarning(new CreateWarningRequest(2L)));
    }

    @Test void auditFailureRollsBackTheWarning() {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("test failure")).when(activities).save(any(ActivityLog.class));
        assertThrows(org.springframework.dao.DataAccessException.class, this::create);
        assertEquals(0L, count("warning"));
        assertEquals(0L, count("activity_log"));
    }

    @Test void jwtMatchesExistingIdentityContract() {
        var principal = (CustomUserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String token = jwt.generateToken(principal);
        assertEquals(1L, jwt.extractUserId(token));
        assertTrue(jwt.isTokenValid(token, principal));
        assertThrows(RuntimeException.class, () -> jwt.extractUserId(token + "tampered"));
    }

    @Test void validationRejectsBadRequests() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new DecisionRequest("UNKNOWN", " ")).isEmpty());
            assertFalse(validator.validate(new CreateInterventionRequest(" ", null, LocalDate.now().minusDays(1))).isEmpty());
            assertFalse(validator.validate(new CreateFollowUpRequest(null, "BAD", "", null, null)).isEmpty());
        }
        assertThrows(InvalidRequestException.class, () -> warnings.getWarnings(1L, null, 0, 101));
    }

    @Test void httpAuthenticationValidationAndErrorContract() throws Exception {
        var principal = (CustomUserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String token = jwt.generateToken(principal);
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
        mvc.perform(get("/api/warnings").param("msmeId", "1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/warnings").param("msmeId", "1").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/warnings").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.assessmentId").exists());
        mvc.perform(post("/api/warnings").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"assessmentId\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.warningStatus").value("OPEN"))
                .andExpect(header().exists("Location"));
        mvc.perform(post("/api/warnings").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"assessmentId\":1}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/warnings/999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.path").value("/api/warnings/999"));
        mvc.perform(get("/api/warnings").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        jdbc.update("update app_user set account_status='INACTIVE' where user_id=1");
        mvc.perform(get("/api/warnings").param("msmeId", "1").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test void httpHistoryAndDatabaseFailure() throws Exception {
        long id = create();
        var principal = (CustomUserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String token = jwt.generateToken(principal);
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
        mvc.perform(get("/api/warnings/" + id + "/history").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].actionCode").value("WARNING_CREATED"));
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("private SQL detail")).when(activities).save(any(ActivityLog.class));
        mvc.perform(post("/api/warnings/" + id + "/decisions").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"decision\":\"INTERVENE\",\"reason\":\"Proceed\",\"followUpDate\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("Database operation unavailable"));
    }

    private long create() { return warnings.createWarning(new CreateWarningRequest(1L)).getWarningId(); }
    private void decide(long id, String action, String note) {
        var request = new DecisionRequest(action, note);
        request.setFollowUpDate(LocalDate.now().plusDays(7));
        warnings.decide(id, request);
    }
    private long interventionId(long warning) { return jdbc.queryForObject("select intervention_id from intervention where warning_id=?", Long.class, warning); }
    private String text(String column, long intervention) { return jdbc.queryForObject("select " + column + " from intervention where intervention_id=?", String.class, intervention); }
    private long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }
    private void assessment(long id, long msme, LocalDate period, String severity) {
        jdbc.update("insert into financial_snapshot(snapshot_id,msme_id,reporting_period) values(?,?,?)", id, msme, java.sql.Date.valueOf(period));
        jdbc.update("insert into risk_assessment(assessment_id,msme_id,snapshot_id,severity,assessment_date) values(?,?,?,?,?)", id, msme, id, severity, Timestamp.valueOf(period.atStartOfDay()));
    }
    private static void login(String role, Long id, Long msme) {
        var user = new AppUser(); user.setUserId(id); user.setRole(role); user.setMsmeId(msme);
        user.setAccountStatus("ACTIVE"); user.setEmail("user@test"); user.setName("User"); user.setPasswordHash("hash");
        var principal = new CustomUserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
