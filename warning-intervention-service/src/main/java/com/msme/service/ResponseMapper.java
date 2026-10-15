package com.msme.service;
import com.msme.entity.*;
import com.msme.dto.warning.*;
import com.msme.dto.intervention.*;
import com.msme.dto.followup.*;
final class ResponseMapper {
    private ResponseMapper() {}
    static WarningResponse toResponse(Warning value) { return new WarningResponse(value.getWarningId(), value.getAssessmentId(), value.getWarningStatus(), value.getPrimaryDriver(), value.getWhyNowText(), value.getRecommendedAction(), value.getCreatedAt(), value.getClosedAt()); }
    static InterventionResponse toResponse(Intervention value) { return new InterventionResponse(value.getInterventionId(), value.getWarningId(), value.getCreatedByUserId(), value.getActionText(), value.getNotes(), value.getIsActive(), value.getFollowUpDate(), value.getCreatedAt(), value.getClosedAt(), value.getClosedByUserId()); }
    static FollowUpReviewResponse toResponse(FollowUpReview value) { return new FollowUpReviewResponse(value.getFollowupId(), value.getInterventionId(), value.getAssessmentId(), value.getReviewedByUserId(), value.getOutcome(), value.getReviewNotes(), value.getReviewedAt(), value.getKeepInterventionActive()); }
    static ActivityLogResponse toResponse(ActivityLog value) { return new ActivityLogResponse(value.getActivityId(), value.getMsmeId(), value.getActorUserId(), value.getActorType(), value.getActionCode(), value.getRelatedEntityType(), value.getRelatedEntityId(), value.getDescription(), value.getCreatedAt(), value.getMetadataJson()); }
}
