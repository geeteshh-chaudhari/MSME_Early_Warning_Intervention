package com.msme.dto.warning;

import java.time.*;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public class DecisionRequest {
    @NotBlank @Pattern(regexp = "ACCEPT|REJECT|ESCALATE|RETURN|CLOSE|CONFIRM")
    private String decision;

    @NotBlank @Size(max = 4000)
    private String reason;

    public DecisionRequest() {}

    public DecisionRequest(String decision, String reason) {
        this.decision = decision;
        this.reason = reason;
    }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
