
package com.msme.entity;

import java.time.LocalDateTime;

import com.msme.enums.Severity;
import com.msme.enums.StressPattern;

public record RiskAssessment(

        Long assessmentId,

        Long msmeId,

        Long snapshotId,

        Long thresholdConfigId,

        LocalDateTime assessmentDate,

        Severity severity,

        StressPattern stressPattern,

        Long previousAssessmentId,

        LocalDateTime createdAt

) {

    public boolean isHealthy() {
        return severity == Severity.HEALTHY;
    }

    public boolean hasBreaches() {
        return severity != null
                && severity != Severity.HEALTHY;
    }

    public boolean isHighPriority() {
        return severity == Severity.HIGH_PRIORITY;
    }
}
