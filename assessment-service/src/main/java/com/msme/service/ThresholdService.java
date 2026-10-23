package com.msme.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.msme.client.MsmeAccessClient;
import com.msme.dto.threshold.CreateThresholdRequest;
import com.msme.dto.threshold.ReviewThresholdRequest;
import com.msme.dto.threshold.ThresholdResponse;
import com.msme.entity.ThresholdConfig;
import com.msme.enums.ThresholdStatus;
import com.msme.exception.InvalidRequestException;
import com.msme.exception.ResourceNotFoundException;
import com.msme.repository.ThresholdConfigRepository;

@Service
public class ThresholdService {

    private final ThresholdConfigRepository repository;
    private final MsmeAccessClient msmeAccessClient;

    public ThresholdService(
            ThresholdConfigRepository repository,
            MsmeAccessClient msmeAccessClient) {

        this.repository = repository;
        this.msmeAccessClient = msmeAccessClient;
    }

    @Transactional
    public ThresholdResponse create(
            CreateThresholdRequest request,
            Long adminUserId) {

        if (adminUserId == null || adminUserId <= 0) {
            throw new InvalidRequestException(
                    "Valid Risk Admin user ID is required"
            );
        }

        if (request == null
                || request.msmeId() == null
                || request.msmeId() <= 0
                || request.revenueDeclinePct() == null
                || request.overdueReceivablePct() == null
                || request.creditUtilisationPct() == null) {

            throw new InvalidRequestException(
                    "Complete threshold request is required"
            );
        }

        msmeAccessClient.checkAccess(request.msmeId());

        int version = repository.findNextVersion(
                request.msmeId()
        );

        LocalDateTime effectiveFrom =
                request.effectiveFrom() == null
                        ? LocalDateTime.now()
                        : request.effectiveFrom();

        ThresholdConfig proposal = new ThresholdConfig(
                null,
                request.msmeId(),
                version,
                request.revenueDeclinePct(),
                request.overdueReceivablePct(),
                request.creditUtilisationPct(),
                ThresholdStatus.APPROVED,
                adminUserId,
                null,
                null,
                null,
                null,
                effectiveFrom
        );

        try {
            Long id = repository.save(proposal);

            return getById(id);

        } catch (DataIntegrityViolationException ex) {
            throw new com.msme.exception.DuplicateAssessmentException("A threshold version was saved concurrently; reload and retry");
        }
    }

    @Transactional
    public ThresholdResponse review(
            Long id,
            ReviewThresholdRequest request,
            Long adminUserId) {

        if (adminUserId == null || adminUserId <= 0) {
            throw new InvalidRequestException(
                    "Valid Risk Admin user ID is required"
            );
        }

        if (request == null
                || request.decision() == null
                || request.reason() == null
                || request.reason().isBlank()) {

            throw new InvalidRequestException(
                    "Review decision and reason are required"
            );
        }

        if (request.decision() == ThresholdStatus.PENDING) {
            throw new InvalidRequestException(
                    "Review decision must be APPROVED or REJECTED"
            );
        }

        ThresholdConfig existing = repository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Threshold",
                                id
                        )
                );

        msmeAccessClient.checkAccess(existing.msmeId());

        if (!existing.isPending()) {
            throw new InvalidRequestException(
                    "Only PENDING thresholds can be reviewed"
            );
        }

        int updated = repository.review(
                id,
                request.decision(),
                adminUserId,
                request.reason().trim()
        );

        if (updated != 1) {
            throw new InvalidRequestException(
                    "Threshold has already been reviewed"
            );
        }

        return getById(id);
    }

    @Transactional(readOnly = true)
    public ThresholdResponse getById(Long id) {

        ThresholdConfig threshold = repository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Threshold",
                                id
                        )
                );

        msmeAccessClient.checkAccess(threshold.msmeId());

        return toResponse(threshold);
    }

    @Transactional(readOnly = true)
    public List<ThresholdResponse> getHistory(
            Long msmeId,
            int page,
            int size) {

        validatePagination(page, size);

        msmeAccessClient.checkAccess(msmeId);

        return repository
                .findByMsmeId(msmeId, page, size)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ThresholdConfig getEffectiveThreshold(
            Long msmeId,
            LocalDateTime asOf) {

        msmeAccessClient.checkAccess(msmeId);

        return repository
                .findLatestApprovedEffective(msmeId, asOf)
                .orElseThrow(() ->
                        new InvalidRequestException(
                                "No threshold configuration "
                                        + "exists for MSME "
                                        + msmeId
                        )
                );
    }

    public long countHistory(Long msmeId) {

        msmeAccessClient.checkAccess(msmeId);

        return repository.countByMsmeId(msmeId);
    }

    private void validatePagination(
            int page,
            int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException(
                    "Page must be >= 0 and size must be 1-100"
            );
        }
    }

    private ThresholdResponse toResponse(
            ThresholdConfig threshold) {

        return new ThresholdResponse(
                threshold.thresholdConfigId(),
                threshold.msmeId(),
                threshold.versionNo(),
                threshold.revenueDeclinePct(),
                threshold.overdueReceivablePct(),
                threshold.creditUtilisationPct(),
                threshold.status(),
                threshold.proposedByUserId(),
                threshold.proposedAt(),
                threshold.reviewedByUserId(),
                threshold.reviewedAt(),
                threshold.reviewReason(),
                threshold.effectiveFrom()
        );
    }
}