
package com.msme.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public ThresholdService(
            ThresholdConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ThresholdResponse create(
            CreateThresholdRequest request,
            Long adminUserId) {

        if (adminUserId == null || adminUserId <= 0) {
            throw new InvalidRequestException(
                    "Valid Risk Admin user ID is required");
        }

        if (request == null || request.msmeId() == null
                || request.msmeId() <= 0
                || request.revenueDeclinePct() == null
                || request.overdueReceivablePct() == null
                || request.creditUtilisationPct() == null) {
            throw new InvalidRequestException(
                    "Complete threshold request is required");
        }

        int version = repository.findNextVersion(
                request.msmeId());

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
                ThresholdStatus.PENDING,
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
            throw new InvalidRequestException(
                "Threshold proposal conflicts with an existing "
                + "version or referenced MSME/user record",
                ex
            );
        }
    }

    @Transactional
    public ThresholdResponse review(
            Long id,
            ReviewThresholdRequest request,
            Long adminUserId) {

        if (adminUserId == null || adminUserId <= 0) {
            throw new InvalidRequestException(
                    "Valid Risk Admin user ID is required");
        }

        if (request == null || request.decision() == null
                || request.reason() == null
                || request.reason().isBlank()) {
            throw new InvalidRequestException(
                    "Review decision and reason are required");
        }

        if (request.decision() == ThresholdStatus.PENDING) {
            throw new InvalidRequestException(
                    "Review decision must be APPROVED or REJECTED");
        }

        ThresholdConfig existing = repository.findById(id)
                .orElseThrow(() ->
                    new ResourceNotFoundException(
                            "Threshold", id));

        if (!existing.isPending()) {
            throw new InvalidRequestException(
                    "Only PENDING thresholds can be reviewed");
        }

        int updated = repository.review(
                id,
                request.decision(),
                adminUserId,
                request.reason().trim()
        );

        if (updated != 1) {
            throw new InvalidRequestException(
                    "Threshold has already been reviewed");
        }

        return getById(id);
    }

    @Transactional(readOnly = true)
    public ThresholdResponse getById(Long id) {

        ThresholdConfig threshold = repository.findById(id)
                .orElseThrow(() ->
                    new ResourceNotFoundException(
                            "Threshold", id));

        return toResponse(threshold);
    }

    @Transactional(readOnly = true)
    public List<ThresholdResponse> getHistory(
            Long msmeId,
            int page,
            int size) {

        validatePagination(page, size);

        return repository.findByMsmeId(msmeId, page, size)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ThresholdConfig getEffectiveThreshold(
            Long msmeId,
            LocalDateTime asOf) {

        return repository.findLatestApprovedEffective(
                    msmeId, asOf)
                .orElseThrow(() ->
                    new InvalidRequestException(
                        "No approved effective threshold "
                        + "exists for MSME " + msmeId));
    }

    public long countHistory(Long msmeId) {
        return repository.countByMsmeId(msmeId);
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException(
                    "Page must be >= 0 and size must be 1-100");
        }
    }

    private ThresholdResponse toResponse(
            ThresholdConfig t) {

        return new ThresholdResponse(
                t.thresholdConfigId(),
                t.msmeId(),
                t.versionNo(),
                t.revenueDeclinePct(),
                t.overdueReceivablePct(),
                t.creditUtilisationPct(),
                t.status(),
                t.proposedByUserId(),
                t.proposedAt(),
                t.reviewedByUserId(),
                t.reviewedAt(),
                t.reviewReason(),
                t.effectiveFrom()
        );
    }
}
