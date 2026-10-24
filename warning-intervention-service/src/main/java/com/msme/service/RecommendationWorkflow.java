package com.msme.service;

import java.time.Duration;
import java.util.*;
import com.msme.dto.warning.WarningResponse;
import com.msme.repository.*;
import com.msme.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

/** Python is the sole recommendation writer; inference holds no Java DB locks. */
@Service
public class RecommendationWorkflow {
    public static final String UNAVAILABLE = "reccommendation service not available";
    private final WarningRepository warnings;
    private final FinancialSnapshotRepository snapshots;
    private final ActivityLogRepository activities;
    private final WarningService cases;
    private final RestClient client;
    private final TransactionTemplate tx;
    private final Object[] locks = new Object[64];

    public RecommendationWorkflow(WarningRepository warnings, InterventionRepository interventions,
            FinancialSnapshotRepository snapshots, ActivityLogRepository activities, WarningService cases,
            @Value("${recommendation.base-url:http://localhost:8085}") String url,
            @Value("${app.internal.token}") String token, PlatformTransactionManager manager) {
        this.warnings=warnings; this.snapshots=snapshots; this.activities=activities; this.cases=cases;
        tx=new TransactionTemplate(manager);
        Arrays.setAll(locks, i -> new Object());
        var factory=new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofMinutes(10));
        client=RestClient.builder().baseUrl(url).requestFactory(factory)
                .defaultHeader("X-Internal-Token",token).build();
    }
    private static boolean filled(String value) { return value != null && !value.isBlank() && !UNAVAILABLE.equals(value); }
    public boolean generated(Long id) {
        return warnings.findById(id).map(w -> filled(w.getRecommendedAction())).orElse(false);
    }
    public Map<String,Object> state(Long id) {
        var w=cases.readable(id);
        return Map.of("generated",filled(w.getRecommendedAction()),
                "actionText",Objects.toString(w.getRecommendedAction(),""),
                "escalated",cases.isEscalated(id),"adminNotesAvailable",cases.adminNotesAvailable(id));
    }
    public WarningResponse explicitlyGenerate(Long id) { return generate(id,true); }
    public WarningResponse generate(Long id) { return generate(id,false); }
    private WarningResponse generate(Long id, boolean manual) {
        synchronized(locks[Math.floorMod(id.hashCode(),locks.length)]) {
            var initial=tx.execute(status -> {
                var w=manual ? cases.writable(id) : warnings.findLockedById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Warning not found"));
                if(manual) cases.requireOwner(w);
                return w;
            });
            if(WarningService.isClosed(initial) || filled(initial.getRecommendedAction()))
                return ResponseMapper.toResponse(initial);
            boolean success=false;
            try {
                var assessment=cases.assessment(initial.getAssessmentId());
                var snapshot=snapshots.findById(assessment.getSnapshotId()).orElseThrow();
                client.post().uri("/internal/recommendations")
                        .body(Map.of("warning_id",id,"msme_id",assessment.getMsmeId(),
                                "as_of",snapshot.getReportingPeriod().toString().substring(0,7)))
                        .retrieve().toBodilessEntity();
                success=true;
            } catch(Exception ex) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                        "Recommendation unavailable for warning {} ({}); unavailable marker retained",
                        id,ex.getClass().getSimpleName());
            }
            final boolean responseReceived=success;
            return tx.execute(status -> {
                var current=warnings.findLockedById(id).orElseThrow();
                // Never copy a model response over a concurrent save or a closed case.
                if(responseReceived && filled(current.getRecommendedAction())
                        && !activities.existsByRelatedEntityTypeAndRelatedEntityIdAndActionCode(
                                "WARNING",id,"RECOMMENDATION_GENERATED"))
                    cases.systemAudit(current,"RECOMMENDATION_GENERATED",
                            "Recommendation JSON saved once",current.getRecommendedAction());
                else if(!filled(current.getRecommendedAction()) && !WarningService.isClosed(current)) {
                    current.setRecommendedAction(UNAVAILABLE);
                    warnings.save(current);
                    cases.systemAudit(current,"RECOMMENDATION_UNAVAILABLE",
                            "Recommendation service not available; explicit retry permitted","{}");
                }
                return ResponseMapper.toResponse(current);
            });
        }
    }
    public boolean attempted(Long id) {
        return generated(id) || activities.existsByRelatedEntityTypeAndRelatedEntityIdAndActionCode(
                "WARNING",id,"RECOMMENDATION_UNAVAILABLE");
    }
}
