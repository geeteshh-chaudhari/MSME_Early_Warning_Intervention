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
import tools.jackson.databind.json.JsonMapper;

/** Saves model actions once; no database lock is held during inference. */
@Service
public class RecommendationWorkflow {
    public static final String UNAVAILABLE="{\"status\":\"unavailable\",\"actions\":[]}";
    private final WarningRepository warnings;
    private final InterventionRepository interventions;
    private final FinancialSnapshotRepository snapshots;
    private final ActivityLogRepository activities;
    private final WarningService cases;
    private final RestClient client;
    private final TransactionTemplate tx;
    private final JsonMapper json=JsonMapper.builder().build();
    private final Object[] locks=new Object[64];

    public RecommendationWorkflow(WarningRepository warnings, InterventionRepository interventions,
            FinancialSnapshotRepository snapshots, ActivityLogRepository activities, WarningService cases,
            @Value("${recommendation.base-url:http://localhost:8085}") String url,
            @Value("${app.internal.token:}") String token, PlatformTransactionManager manager) {
        this.warnings=warnings; this.interventions=interventions; this.snapshots=snapshots;
        this.activities=activities; this.cases=cases; tx=new TransactionTemplate(manager);
        Arrays.setAll(locks,i->new Object());
        var factory=new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofMinutes(10));
        client=RestClient.builder().baseUrl(url).requestFactory(factory).defaultHeader("X-Internal-Token",token).build();
    }
    public boolean generated(Long id) {
        return activities.existsByRelatedEntityTypeAndRelatedEntityIdAndActionCode("WARNING",id,"RECOMMENDATION_GENERATED");
    }
    public Map<String,Object> state(Long id) {
        var w=cases.readable(id);
        return Map.of("generated",generated(id),"actionText",Objects.toString(w.getRecommendedAction(),UNAVAILABLE),"escalated",cases.isEscalated(id),"adminNotesAvailable",cases.adminNotesAvailable(id));
    }
    public WarningResponse explicitlyGenerate(Long id) { return generate(id,true); }
    public WarningResponse generate(Long id) { return generate(id,false); }
    private WarningResponse generate(Long id, boolean manual) {
        synchronized(locks[Math.floorMod(id.hashCode(),locks.length)]) {
            var initial=tx.execute(status->{
                var w=manual ? cases.writable(id) : warnings.findLockedById(id).orElseThrow(()->new ResourceNotFoundException("Warning not found"));
                if(manual)cases.requireOwner(w);
                return w;
            });
            if(WarningService.isClosed(initial) || generated(id)) return ResponseMapper.toResponse(initial);
            String actionText=UNAVAILABLE, evidence="{}";
            boolean success=false;
            try {
                var a=cases.assessment(initial.getAssessmentId());
                var s=snapshots.findById(a.getSnapshotId()).orElseThrow();
                String raw=client.post().uri("/internal/recommendations")
                    .body(Map.of("msme_id",a.getMsmeId(),"as_of",s.getReportingPeriod().toString().substring(0,7)))
                    .retrieve().body(String.class);
                @SuppressWarnings("unchecked")
                Map<String,Object> result=raw==null ? null : json.readValue(raw,Map.class);
                if(result==null || !"generated".equals(result.get("status")))throw new IllegalStateException("No generated result");
                var recommendation=(Map<?,?>)result.get("recommendation");
                var actions=(List<?>)recommendation.get("actions");
                var lines=new ArrayList<String>();
                for(var item:actions) {
                    var action=(Map<?,?>)item;
                    var text=Objects.toString(action.get("suggested_action"),"").trim();
                    if(!text.isBlank())lines.add((lines.size()+1)+". "+Objects.toString(action.get("title"),"")+": "+text);
                }
                if(lines.isEmpty())throw new IllegalStateException("No suggested actions");
                actionText=json.writeValueAsString(recommendation); evidence=raw; success=true;
            } catch(Exception ex) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Recommendation unavailable for warning {} ({})",id,ex.getClass().getSimpleName());
            }
            final String savedText=actionText, savedEvidence=evidence;
            final boolean savedSuccess=success;
            return tx.execute(status->{
                var w=manual ? cases.writable(id) : warnings.findLockedById(id).orElseThrow();
                if(manual)cases.requireOwner(w);
                if(WarningService.isClosed(w) || generated(id))return ResponseMapper.toResponse(w);
                w.setRecommendedAction(savedText);
                cases.systemAudit(w,savedSuccess?"RECOMMENDATION_GENERATED":"RECOMMENDATION_UNAVAILABLE",
                    savedSuccess?"Immutable LLM actions and evidence saved":UNAVAILABLE,savedEvidence);
                return ResponseMapper.toResponse(w);
            });
        }
    }
    public boolean attempted(Long id) {
        return generated(id) || activities.existsByRelatedEntityTypeAndRelatedEntityIdAndActionCode("WARNING",id,"RECOMMENDATION_UNAVAILABLE");
    }
}

