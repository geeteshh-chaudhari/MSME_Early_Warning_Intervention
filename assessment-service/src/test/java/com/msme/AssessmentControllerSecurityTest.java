package com.msme;

import com.msme.controller.AssessmentController;
import com.msme.dto.assessment.AssessmentFromFinanceRequest;
import com.msme.dto.assessment.CreateAssessmentRequest;
import com.msme.service.AssessmentComparisonService;
import com.msme.service.AssessmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;

@SpringJUnitConfig(AssessmentControllerSecurityTest.Config.class)
class AssessmentControllerSecurityTest {
    @Autowired AssessmentController controller;
    @Autowired AssessmentService service;

    @BeforeEach
    void resetService() {
        reset(service);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @WithMockUser(roles = "RM")
    void rmCanRunAssessmentsManually() {
        var direct = new CreateAssessmentRequest(100L, List.of());
        var finance = new AssessmentFromFinanceRequest(100L, LocalDate.of(2026, 9, 1));

        controller.createAssessment(direct);
        controller.createFromFinance(finance);

        verify(service).create(direct);
        verify(service).createFromFinance(finance);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RISK_ADMIN", "MSME_REP"})
    void otherRolesCannotRunAssessments(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                authenticated("user", "unused", createAuthorityList("ROLE_" + role)));

        assertThrows(AccessDeniedException.class,
                () -> controller.createAssessment(new CreateAssessmentRequest(100L, List.of())));
        assertThrows(AccessDeniedException.class,
                () -> controller.createFromFinance(
                        new AssessmentFromFinanceRequest(100L, LocalDate.of(2026, 9, 1))));

        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "RISK_ADMIN")
    void riskAdminCanStillViewAssessmentHistory() {
        controller.getHistory(100L, 0, 10);

        verify(service).getHistory(100L, 0, 10);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Config {
        @Bean
        AssessmentService assessmentService() {
            return mock(AssessmentService.class);
        }

        @Bean
        AssessmentController assessmentController(AssessmentService service) {
            return new AssessmentController(service, mock(AssessmentComparisonService.class));
        }
    }
}
