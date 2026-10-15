package com.msme.entity;

import java.time.*;
import java.math.BigDecimal;
import jakarta.persistence.*;

@Entity
@Table(name = "ACTIVITY_LOG")
public class ActivityLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ACTIVITY_ID")
    private Long activityId;

    @Column(name = "MSME_ID")
    private Long msmeId;

    @Column(name = "ACTOR_USER_ID")
    private Long actorUserId;

    @Column(name = "ACTOR_TYPE")
    private String actorType;

    @Column(name = "ACTION_CODE")
    private String actionCode;

    @Column(name = "RELATED_ENTITY_TYPE")
    private String relatedEntityType;

    @Column(name = "RELATED_ENTITY_ID")
    private Long relatedEntityId;

    @Lob
    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Lob
    @Column(name = "METADATA_JSON")
    private String metadataJson;

    public ActivityLog() {}

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
