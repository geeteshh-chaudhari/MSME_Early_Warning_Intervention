package com.msme.dto.intervention;

import java.time.*;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public class UpdateInterventionRequest {
    @NotBlank @Size(max = 4000)
    private String notes;

    @NotNull @FutureOrPresent
    private LocalDate followUpDate;

    public UpdateInterventionRequest() {}

    public UpdateInterventionRequest(String notes, LocalDate followUpDate) {
        this.notes = notes;
        this.followUpDate = followUpDate;
    }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDate getFollowUpDate() { return followUpDate; }
    public void setFollowUpDate(LocalDate followUpDate) { this.followUpDate = followUpDate; }
}
