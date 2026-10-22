package com.msme.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "workflow.enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowSchedulingConfig {
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        // Model inference must not block assessment-to-warning reconciliation.
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("warning-workflow-");
        return scheduler;
    }
}
