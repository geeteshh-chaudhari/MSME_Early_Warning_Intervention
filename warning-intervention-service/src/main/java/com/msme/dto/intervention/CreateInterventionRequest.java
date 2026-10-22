package com.msme.dto.intervention;

import java.time.*;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public class CreateInterventionRequest {
    @NotBlank @Pattern(regexp = "intervene|escalate|close")
    private String actionText;

    @NotBlank @Size(max = 4000)
    private String notes;

    @NotNull @FutureOrPresent
    private LocalDate followUpDate;

    public CreateInterventionRequest() {}

    public CreateInterventionRequest(String actionText, String notes, LocalDate followUpDate) {
        this.actionText = actionText;
        this.notes = notes;
        this.followUpDate = followUpDate;
    }
    public String getActionText() { return actionText; }
    public void setActionText(String actionText) { this.actionText = actionText; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDate getFollowUpDate() { return followUpDate; }
    public void setFollowUpDate(LocalDate followUpDate) { this.followUpDate = followUpDate; }
}
