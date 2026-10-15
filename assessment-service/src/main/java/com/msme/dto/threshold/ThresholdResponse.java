
package com.msme.dto.threshold;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.msme.enums.ThresholdStatus;

public record ThresholdResponse(

        Long thresholdConfigId,

        Long msmeId,

        Integer versionNo,

        BigDecimal revenueDeclinePct,

        BigDecimal overdueReceivablePct,

        BigDecimal creditUtilisationPct,

        ThresholdStatus status,

        Long proposedByUserId,

        LocalDateTime proposedAt,

        Long reviewedByUserId,

        LocalDateTime reviewedAt,

        String reviewReason,

        LocalDateTime effectiveFrom

) {}
