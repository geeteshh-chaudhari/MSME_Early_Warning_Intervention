package com.msme.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;

/** Only a newly inserted warning emits this event; no existing-warning scan. */
@Component
@ConditionalOnProperty(name="workflow.enabled", havingValue="true", matchIfMissing=true)
public class NewWarningRecommendationListener {
    private final RecommendationWorkflow recommendations;
    private final Executor executor;
    public NewWarningRecommendationListener(RecommendationWorkflow recommendations,
            @Qualifier("recommendationExecutor") Executor executor) {
        this.recommendations = recommendations;
        this.executor = executor;
    }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void created(WarningCreatedEvent event) {
        try {
            executor.execute(() -> {
                try { recommendations.generate(event.warningId()); }
                catch (Exception ex) {
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                            "Initial recommendation failed for warning {} ({})",
                            event.warningId(), ex.getClass().getSimpleName());
                }
            });
        } catch (org.springframework.core.task.TaskRejectedException ex) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                    "Initial recommendation queue full for warning {}; field remains empty", event.warningId());
        }
    }
}
