package com.msme.config;

import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/** Documentation only. Runtime authorization remains in the existing security/services. */
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI serviceOpenApi(@Value("${spring.application.name}") String serviceName) {
        return new OpenAPI()
            .info(new Info().title(serviceName + " API").version("1.0.0")
                .description("MSME Early Warning and Intervention. Sign in using POST /api/auth/login "
                    + "in the MSME definition, then paste accessToken into Authorize without a Bearer prefix. "
                    + "Current account, role and business assignment are checked on protected requests."))
            .servers(List.of(new Server().url("/").description("Current origin / API Gateway")))
            .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    public OpenApiCustomizer documentOperations() {
        return api -> {
            if (api.getPaths() == null) return;
            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                String verb = method.name();
                boolean login = path.equals("/api/auth/login");
                if (login) operation.setSecurity(List.of());
                if (operation.getSummary() == null && operation.getOperationId() != null) {
                    String words = operation.getOperationId().replaceAll("_\\d+$", "")
                        .replaceAll("([a-z0-9])([A-Z])", "$1 $2");
                    operation.setSummary(Character.toUpperCase(words.charAt(0)) + words.substring(1));
                }
                operation.setTags(List.of(tag(path)));
                List<String> roles = roles(path, verb);
                operation.addExtension("x-roles", roles);
                String access = login ? "Public login. Returns accessToken, user identity and role."
                    : "Allowed roles: " + String.join(", ", roles)
                        + ". RMs are restricted to assigned businesses and representatives to their linked MSME where applicable. "
                        + "Role and ownership rules are enforced by the existing application.";
                operation.setDescription((operation.getDescription() == null ? "" : operation.getDescription() + "\n\n") + access);
                if (operation.getResponses() != null) {
                    operation.getResponses().addApiResponse("401", new ApiResponse().description(
                        login ? "Invalid credentials or inactive account" : "Missing, invalid or expired JWT, or inactive/mismatched current account"));
                    if (!login) operation.getResponses().addApiResponse("403", new ApiResponse().description("Role or business assignment does not permit this operation"));
                    boolean created = verb.equals("POST") && !login && !path.endsWith("/decisions") && !path.endsWith("/review");
                    if (created && operation.getResponses().containsKey("200") && !operation.getResponses().containsKey("201")) {
                        ApiResponse response = operation.getResponses().remove("200");
                        response.setDescription("Created");
                        operation.getResponses().addApiResponse("201", response);
                    }
                }
            }));
        };
    }

    private static String tag(String path) {
        if (path.startsWith("/api/auth")) return "Authentication";
        if (path.startsWith("/api/users")) return "Users and representatives";
        if (path.startsWith("/api/msmes")) return "MSME profiles";
        if (path.startsWith("/api/snapshots")) return "Financial snapshots";
        if (path.startsWith("/api/assessment-data")) return "Assessment input and readiness";
        if (path.contains("/thresholds")) return "Threshold governance";
        if (path.startsWith("/api/assessment")) return "Risk assessments";
        if (path.endsWith("/follow-ups")) return "Follow-up reviews";
        if (path.contains("/interventions")) return "Interventions";
        if (path.endsWith("/history")) return "Warning activity history";
        return "Warnings and decisions";
    }

    private static List<String> roles(String path, String method) {
        var all = List.of("RISK_ADMIN", "RM", "MSME_REP");
        var staff = List.of("RISK_ADMIN", "RM");
        if (path.equals("/api/auth/login")) return List.of();
        if (path.startsWith("/api/assessment/"))
            return method.equals("POST") && path.contains("/thresholds") ? List.of("RISK_ADMIN") : staff;
        if (path.startsWith("/api/assessment-data/")) return all;
        if (path.startsWith("/api/users")) {
            if (path.equals("/api/users/me")) return all;
            if (path.contains("/my-") || method.equals("POST") || method.equals("PUT")) return List.of("RM");
            if (path.equals("/api/users/msme-representatives/{userId}")) return staff;
            return List.of("RISK_ADMIN");
        }
        if (path.startsWith("/api/msmes")) {
            if (path.equals("/api/msmes/me")) return List.of("MSME_REP");
            if (!method.equals("GET") || path.equals("/api/msmes/my")) return List.of("RM");
            if (path.equals("/api/msmes") || path.contains("/rm/")) return List.of("RISK_ADMIN");
            return all;
        }
        if (path.startsWith("/api/snapshots")) {
            if (method.equals("POST") || path.equals("/api/snapshots/my") || path.contains("/readiness/")) return List.of("MSME_REP");
            if (path.equals("/api/snapshots")) return List.of("RISK_ADMIN");
            return path.contains("/period/") ? all : staff;
        }
        if (path.endsWith("/recommendation")) return List.of("RM");
        if (path.contains("/interventions")) return method.equals("GET") ? all : List.of("RM");
        if (path.endsWith("/history") || !method.equals("GET")) return staff;
        return all;
    }
}
