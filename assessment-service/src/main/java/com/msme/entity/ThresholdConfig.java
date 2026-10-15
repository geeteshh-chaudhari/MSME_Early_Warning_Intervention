
package com.msme.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.msme.enums.ThresholdStatus;

public record ThresholdConfig(

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

) {

    public boolean isPending() {
        return status == ThresholdStatus.PENDING;
    }

    public boolean isApproved() {
        return status == ThresholdStatus.APPROVED;
    }

    public boolean isRejected() {
        return status == ThresholdStatus.REJECTED;
    }

    public boolean isEffectiveAt(LocalDateTime dateTime) {

        if (dateTime == null) {
            return false;
        }

        return isApproved()
                && effectiveFrom != null
                && !effectiveFrom.isAfter(dateTime);
    }
}
