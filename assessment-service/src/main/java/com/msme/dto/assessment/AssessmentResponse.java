
package com.msme.dto.assessment;

import java.time.LocalDateTime;
import java.util.List;

import com.msme.enums.Severity;
import com.msme.enums.StressPattern;

public record AssessmentResponse(

        Long assessmentId,

        Long msmeId,

        Long snapshotId,

        Long thresholdConfigId,

        LocalDateTime assessmentDate,

        Severity severity,

        StressPattern stressPattern,

        Long previousAssessmentId,

        List<IndicatorResultResponse> indicators

) {

    public AssessmentResponse {
        if (indicators != null) {
            indicators = List.copyOf(indicators);
        }
    }
}
