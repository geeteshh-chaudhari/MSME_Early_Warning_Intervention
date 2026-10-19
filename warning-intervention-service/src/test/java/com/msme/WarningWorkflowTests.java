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

    @Test void documentationIsPublicButBusinessApiStillRequiresJwt() throws Exception {
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        var mvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.servers[0].url").value("/"))
            .andExpect(jsonPath("$.security[0].bearerAuth").isArray())
            .andExpect(jsonPath("$.paths['/api/warnings'].post.responses['201']").exists());
        mvc.perform(get("/api/warnings").param("msmeId", "1")).andExpect(status().isUnauthorized());
    }

    @Test void evidenceAndDeduplication() {
        var result = create();
        assertTrue(result.getWhyNowText().contains("Revenue fell"));
        assertTrue(result.getWhyNowText().contains("15"));
        assertEquals("OPEN", result.getWarningStatus());
        assertThrows(DuplicateResourceException.class, this::create);
        assertEquals(1L, count("warning"));
        assertEquals(1L, count("activity_log"));
    }

    @Test void healthyAssessmentCannotWarn() {
        assertThrows(InvalidRequestException.class, () -> warnings.createWarning(new CreateWarningRequest(3L)));
    }

    @Test void assessmentWithoutBreachesCannotWarn() {
        assertThrows(InvalidRequestException.class, () -> warnings.createWarning(new CreateWarningRequest(2L)));
    }

    @Test void acceptInterveneFollowUpAndClose() {
        long warning = create().getWarningId();
        warnings.decide(warning, new DecisionRequest("ACCEPT", "Agree recovery plan"));
        var intervention = start(warning);
        var result = interventions.followUp(intervention, new CreateFollowUpRequest(2L, "IMPROVING", "Collections improved", false, null));
        assertEquals("N", result.getKeepInterventionActive());
        assertEquals("CLOSED", warnings.getWarning(warning).getWarningStatus());
        assertEquals("N", jdbc.queryForObject("select is_active from intervention where intervention_id=?", String.class, intervention));
        assertEquals(4L, count("activity_log"));
        assertThrows(DuplicateResourceException.class, () -> warnings.decide(warning, new DecisionRequest("ESCALATE", "late")));
    }

    @Test void rejectionRequiresReasonAndClosesReview() {
        long id = create().getWarningId();
        assertThrows(InvalidRequestException.class, () -> warnings.decide(id, new DecisionRequest("REJECT", " ")));
        assertEquals("CLOSED_REVIEWED", warnings.decide(id, new DecisionRequest("REJECT", "Data explained")).getWarningStatus());
    }

    @Test void onlyRiskAdminCanReturnAnEscalation() {
        long id = create().getWarningId();
        warnings.decide(id, new DecisionRequest("ESCALATE", "Needs review"));
        assertThrows(ForbiddenOperationException.class, () -> warnings.decide(id, new DecisionRequest("RETURN", "Proceed")));
        login("RISK_ADMIN", 7L, null);
        assertEquals("UNDER_REVIEW", warnings.decide(id, new DecisionRequest("RETURN", "Proceed with intervention")).getWarningStatus());
    }

    @Test void riskAdminClosesEscalationAndItsActiveIntervention() {
        long id = create().getWarningId();
        warnings.decide(id, new DecisionRequest("ACCEPT", "Proceed"));
        start(id);
        warnings.decide(id, new DecisionRequest("ESCALATE", "Review"));
        login("RISK_ADMIN", 7L, null);
        assertEquals("CLOSED", warnings.decide(id, new DecisionRequest("CLOSE", "Resolved by review")).getWarningStatus());
        assertEquals(0L, jdbc.queryForObject("select count(*) from intervention where is_active='Y'", Long.class));
    }

    @Test void confirmEscalationDoesNotCreateAnotherTier() {
        long id = create().getWarningId();
        warnings.decide(id, new DecisionRequest("ESCALATE", "Review"));
        login("RISK_ADMIN", 7L, null);
        assertEquals("UNDER_REVIEW", warnings.decide(id, new DecisionRequest("CONFIRM", "Accepted escalation")).getWarningStatus());
    }

    @Test void ownerAndRepresentativePermissions() {
        long id = create().getWarningId();
        login("RM", 2L, null);
        assertThrows(ForbiddenOperationException.class, () -> warnings.getWarning(id));
        assertThrows(ForbiddenOperationException.class, () -> warnings.decide(id, new DecisionRequest("ACCEPT", "Proceed")));
        login("MSME_REP", 3L, 1L);
        assertEquals(id, warnings.getWarning(id).getWarningId());
        assertThrows(AccessDeniedException.class, () -> warnings.decide(id, new DecisionRequest("ACCEPT", "Proceed")));
        login("MSME_REP", 4L, 2L);
        assertThrows(ForbiddenOperationException.class, () -> warnings.getWarning(id));
    }

    @Test void wrongTransitionAndMissingRecords() {
        long id = create().getWarningId();
        assertThrows(DuplicateResourceException.class, () -> interventions.createIntervention(id,
                new CreateInterventionRequest("Call", null, LocalDate.now().plusDays(1))));
        assertThrows(ResourceNotFoundException.class, () -> warnings.getWarning(999L));
        assertThrows(ResourceNotFoundException.class, () -> interventions.getFollowUps(999L, 0, 20));
    }

    @Test void auditFailureRollsBackTheWarning() {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("test failure")).when(activities).save(any(ActivityLog.class));
        assertThrows(org.springframework.dao.DataAccessException.class, this::create);
        assertEquals(0L, count("warning"));
        assertEquals(0L, count("activity_log"));
    }

    @Test void invalidFollowUpAndDuplicatesDoNotChangeTheCase() {
        long id = create().getWarningId();
        warnings.decide(id, new DecisionRequest("ACCEPT", "Proceed"));
        long intervention = start(id);
        assertThrows(InvalidRequestException.class, () -> interventions.followUp(intervention,
                new CreateFollowUpRequest(1L, "IMPROVING", "Same baseline", true, LocalDate.now().plusDays(30))));
        assertThrows(InvalidRequestException.class, () -> interventions.followUp(intervention,
                new CreateFollowUpRequest(2L, "STILL_STRESSED", "Cannot close", false, null)));
        interventions.followUp(intervention, new CreateFollowUpRequest(2L, "STILL_STRESSED", "Revise plan", true, LocalDate.now().plusDays(30)));
        assertThrows(DuplicateResourceException.class, () -> interventions.followUp(intervention,
                new CreateFollowUpRequest(2L, "STILL_STRESSED", "Duplicate", true, LocalDate.now().plusDays(30))));
        var next = interventions.createIntervention(id, new CreateInterventionRequest("Revised recovery plan", "Further cycle", LocalDate.now().plusDays(30)));
        assertNotEquals(intervention, next.getInterventionId());
        assertEquals(1L, count("follow_up_review"));
        assertEquals(1L, jdbc.queryForObject("select count(*) from intervention where is_active='Y'", Long.class));
    }

    @Test void crossMsmeEvidenceCannotBeUsed() {
        long id = create().getWarningId();
        warnings.decide(id, new DecisionRequest("ACCEPT", "Proceed"));
        long intervention = start(id);
        assessment(4, 2, month.minusMonths(1), "WATCH");
        assertThrows(InvalidRequestException.class, () -> interventions.followUp(intervention,
                new CreateFollowUpRequest(4L, "IMPROVING", "Wrong MSME", false, null)));
        assertEquals(0L, count("follow_up_review"));
    }

    @Test void concurrentRejectionsCreateOneDecision() throws Exception {
        long id = create().getWarningId();
        var executor = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        Callable<Boolean> action = () -> {
            login("RM", 1L, null);
            gate.await();
            try { warnings.decide(id, new DecisionRequest("REJECT", "Resolved")); return true; }
            catch (DuplicateResourceException ex) { return false; }
            finally { SecurityContextHolder.clearContext(); }
        };
        try {
            var first = executor.submit(action); var second = executor.submit(action); gate.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(2L, count("activity_log"));
        } finally { executor.shutdownNow(); }
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

    @Test void failedDecisionAuditRollsBackStatus() {
        long id = create().getWarningId();
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("test failure")).when(activities).save(any(ActivityLog.class));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> warnings.decide(id, new DecisionRequest("REJECT", "Resolved")));
        assertEquals("OPEN", warnings.getWarning(id).getWarningStatus());
        assertEquals(1L, count("activity_log"));
    }

    @Test void failedFollowUpAuditRollsBackReviewAndClosure() {
        long id = create().getWarningId();
        warnings.decide(id, new DecisionRequest("ACCEPT", "Proceed"));
        long intervention = start(id);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("test failure")).when(activities).save(any(ActivityLog.class));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> interventions.followUp(intervention,
                new CreateFollowUpRequest(2L, "IMPROVING", "Improved", false, null)));
        assertEquals(0L, count("follow_up_review"));
        assertEquals("INTERVENTION_ACTIVE", warnings.getWarning(id).getWarningStatus());
        assertEquals("Y", jdbc.queryForObject("select is_active from intervention where intervention_id=?", String.class, intervention));
    }

    @Test void continuedMonitoringNeedsNextDateAndUsesNewMonths() {
        long id = create().getWarningId();
        warnings.decide(id, new DecisionRequest("ACCEPT", "Proceed"));
        long intervention = start(id);
        assertThrows(InvalidRequestException.class, () -> interventions.followUp(intervention,
                new CreateFollowUpRequest(2L, "IMPROVING", "Monitor", true, null)));
        interventions.followUp(intervention, new CreateFollowUpRequest(2L, "IMPROVING", "Monitor", true, LocalDate.now().plusDays(30)));
        interventions.followUp(intervention, new CreateFollowUpRequest(3L, "STABILISING", "Stable", false, null));
        assertEquals(2L, count("follow_up_review"));
        assertEquals("CLOSED", warnings.getWarning(id).getWarningStatus());
    }

    @Test void followUpEscalatesAndRiskAdminReturnsToActiveIntervention() {
        long id = create().getWarningId();
        warnings.decide(id, new DecisionRequest("ACCEPT", "Proceed"));
        long intervention = start(id);
        interventions.followUp(intervention, new CreateFollowUpRequest(2L, "ESCALATED", "Review required", true, LocalDate.now().plusDays(30)));
        assertEquals("ESCALATED", warnings.getWarning(id).getWarningStatus());
        login("RISK_ADMIN", 7L, null);
        assertEquals("INTERVENTION_ACTIVE", warnings.decide(id, new DecisionRequest("RETURN", "Continue plan")).getWarningStatus());
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
        long id = create().getWarningId();
        var principal = (CustomUserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String token = jwt.generateToken(principal);
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
        mvc.perform(get("/api/warnings/" + id + "/history").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].actionCode").value("WARNING_CREATED"));
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("private SQL detail")).when(activities).save(any(ActivityLog.class));
        mvc.perform(post("/api/warnings/" + id + "/decisions").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"decision\":\"ACCEPT\",\"reason\":\"Proceed\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("Database operation unavailable"));
    }

    private WarningResponse create() { return warnings.createWarning(new CreateWarningRequest(1L)); }
    private long start(long warning) {
        var result = interventions.createIntervention(warning, new CreateInterventionRequest("Collections plan", "Call customer", LocalDate.now().plusDays(7)));
        jdbc.update("update intervention set created_at=? where intervention_id=?", Timestamp.valueOf(month.minusMonths(2).atStartOfDay()), result.getInterventionId());
        return result.getInterventionId();
    }
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
