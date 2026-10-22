package com.msme.dto.warning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateWarningNoteRequest {
    @NotBlank @Size(max = 4000)
    private String notes;

    public CreateWarningNoteRequest() {}
    public CreateWarningNoteRequest(String notes) { this.notes = notes; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
