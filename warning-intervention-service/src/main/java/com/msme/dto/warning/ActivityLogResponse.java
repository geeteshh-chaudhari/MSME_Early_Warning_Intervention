package com.msme.dto.warning;

import java.time.*;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public class ActivityLogResponse {
    
    private Long activityId;

    
    private Long msmeId;

    
    private Long actorUserId;

    
    private String actorType;

    
    private String actionCode;

    
    private String relatedEntityType;

    
    private Long relatedEntityId;

    
    private String description;

    
    private LocalDateTime createdAt;

    
    private String metadataJson;

    public ActivityLogResponse() {}

    public ActivityLogResponse(Long activityId, Long msmeId, Long actorUserId, String actorType, String actionCode, String relatedEntityType, Long relatedEntityId, String description, LocalDateTime createdAt, String metadataJson) {
        this.activityId = activityId;
        this.msmeId = msmeId;
        this.actorUserId = actorUserId;
        this.actorType = actorType;
        this.actionCode = actionCode;
        this.relatedEntityType = relatedEntityType;
        this.relatedEntityId = relatedEntityId;
        this.description = description;
        this.createdAt = createdAt;
        this.metadataJson = metadataJson;
    }
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }
    public Long getMsmeId() { return msmeId; }
    public void setMsmeId(Long msmeId) { this.msmeId = msmeId; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public String getActionCode() { return actionCode; }
    public void setActionCode(String actionCode) { this.actionCode = actionCode; }
    public String getRelatedEntityType() { return relatedEntityType; }
    public void setRelatedEntityType(String relatedEntityType) { this.relatedEntityType = relatedEntityType; }
    public Long getRelatedEntityId() { return relatedEntityId; }
    public void setRelatedEntityId(Long relatedEntityId) { this.relatedEntityId = relatedEntityId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
