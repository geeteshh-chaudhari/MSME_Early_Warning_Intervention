
package com.msme.dto.assessment;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateAssessmentRequest(

        @NotNull
        @Positive
        Long msmeId,

        @NotNull
        @Size(min = 3, max = 3)
        List<@NotNull @Valid FinancialSnapshotRequest> snapshots

) {}
