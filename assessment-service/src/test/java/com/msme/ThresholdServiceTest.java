package com.msme;

import com.msme.client.MsmeAccessClient;
import com.msme.dto.threshold.CreateThresholdRequest;
import com.msme.dto.threshold.ReviewThresholdRequest;
import com.msme.dto.threshold.ThresholdResponse;
import com.msme.entity.ThresholdConfig;
import com.msme.enums.ThresholdStatus;
import com.msme.exception.InvalidRequestException;
import com.msme.exception.ResourceNotFoundException;
import com.msme.repository.ThresholdConfigRepository;
import com.msme.service.ThresholdService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThresholdServiceTest {

    @Mock
    private ThresholdConfigRepository repository;

    @Mock
    private MsmeAccessClient accessClient;

    @InjectMocks
    private ThresholdService service;

    private static final LocalDateTime EFFECTIVE_FROM =
            LocalDateTime.of(2026, 9, 1, 0, 0);

    @Test
    void createStoresImmediatelyUsableVersion() {
        when(repository.findNextVersion(100L)).thenReturn(2);
        when(repository.save(any(ThresholdConfig.class))).thenReturn(1L);
        when(repository.findById(1L))
                .thenReturn(Optional.of(threshold(ThresholdStatus.APPROVED)));

        ThresholdResponse result = service.create(createRequest(), 10L);

        ArgumentCaptor<ThresholdConfig> captor =
                ArgumentCaptor.forClass(ThresholdConfig.class);

        verify(repository).save(captor.capture());

        ThresholdConfig saved = captor.getValue();

        assertAll(
                () -> assertEquals(100L, saved.msmeId()),
                () -> assertEquals(2, saved.versionNo().intValue()),
                () -> assertEquals(ThresholdStatus.APPROVED, saved.status()),
                () -> assertEquals(10L, saved.proposedByUserId()),
                () -> assertEquals(new BigDecimal("15"),
                        saved.revenueDeclinePct()),
                () -> assertEquals(new BigDecimal("30"),
                        saved.overdueReceivablePct()),
                () -> assertEquals(new BigDecimal("70"),
                        saved.creditUtilisationPct()),
                () -> assertEquals(EFFECTIVE_FROM, saved.effectiveFrom()),
                () -> assertNull(saved.reviewedByUserId()),
                () -> assertEquals(ThresholdStatus.APPROVED, result.status())
        );

        // Access is checked before saving and again when reading the result.
        verify(accessClient, times(2)).checkAccess(100L);
    }

    @Test
    void createRejectsInvalidAdminId() {
        assertThrows(InvalidRequestException.class,
                () -> service.create(createRequest(), 0L));

        verifyNoInteractions(repository, accessClient);
    }

    @Test
    void createRejectsIncompleteRequest() {
        CreateThresholdRequest incomplete = new CreateThresholdRequest(
                100L, null, new BigDecimal("30"),
                new BigDecimal("70"), EFFECTIVE_FROM
        );

        assertThrows(InvalidRequestException.class,
                () -> service.create(incomplete, 10L));

        verifyNoInteractions(repository, accessClient);
    }

    @Test
    void createStopsWhenMsmeAccessCheckFails() {
        doThrow(new InvalidRequestException("MSME access denied"))
                .when(accessClient).checkAccess(100L);

        assertThrows(InvalidRequestException.class,
                () -> service.create(createRequest(), 10L));

        verifyNoInteractions(repository);
    }

    @Test
    void createConvertsDatabaseConflictToMeaningfulException() {
        when(repository.findNextVersion(100L)).thenReturn(2);
        when(repository.save(any(ThresholdConfig.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate version"));

        assertThrows(com.msme.exception.DuplicateAssessmentException.class,
                () -> service.create(createRequest(), 10L));
    }

    @Test
    void reviewApprovesPendingThresholdAndTrimsReason() {
        when(repository.findById(1L)).thenReturn(
                Optional.of(threshold(ThresholdStatus.PENDING)),
                Optional.of(threshold(ThresholdStatus.APPROVED))
        );

        when(repository.review(
                1L, ThresholdStatus.APPROVED, 20L, "Approved"))
                .thenReturn(1);

        ThresholdResponse result = service.review(
                1L,
                new ReviewThresholdRequest(
                        ThresholdStatus.APPROVED, " Approved "),
                20L
        );

        assertEquals(ThresholdStatus.APPROVED, result.status());

        verify(repository).review(
                1L, ThresholdStatus.APPROVED, 20L, "Approved");
    }

    @Test
    void reviewRejectsPendingThresholdWithReason() {
        when(repository.findById(1L)).thenReturn(
                Optional.of(threshold(ThresholdStatus.PENDING)),
                Optional.of(threshold(ThresholdStatus.REJECTED))
        );

        when(repository.review(
                1L, ThresholdStatus.REJECTED, 20L, "Needs revision"))
                .thenReturn(1);

        ThresholdResponse result = service.review(
                1L,
                new ReviewThresholdRequest(
                        ThresholdStatus.REJECTED, "Needs revision"),
                20L
        );

        assertEquals(ThresholdStatus.REJECTED, result.status());
    }

    @Test
    void reviewRejectsAlreadyReviewedThreshold() {
        when(repository.findById(1L))
                .thenReturn(Optional.of(
                        threshold(ThresholdStatus.APPROVED)));

        assertThrows(InvalidRequestException.class,
                () -> service.review(
                        1L,
                        new ReviewThresholdRequest(
                                ThresholdStatus.REJECTED, "Change decision"),
                        20L));

        verify(repository, never()).review(
                any(), any(), any(), any());
    }

    @Test
    void reviewRequiresNonBlankReason() {
        assertThrows(InvalidRequestException.class,
                () -> service.review(
                        1L,
                        new ReviewThresholdRequest(
                                ThresholdStatus.APPROVED, " "),
                        20L));

        verifyNoInteractions(repository, accessClient);
    }

    @Test
    void getByIdRejectsMissingThreshold() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getById(999L));

        verifyNoInteractions(accessClient);
    }

    @Test
    void reviewDetectsConcurrentReview() {
        when(repository.findById(1L))
                .thenReturn(Optional.of(
                        threshold(ThresholdStatus.PENDING)));

        // Zero updated rows means another review won the race.
        when(repository.review(
                1L, ThresholdStatus.APPROVED, 20L, "Approved"))
                .thenReturn(0);

        assertThrows(InvalidRequestException.class,
                () -> service.review(
                        1L,
                        new ReviewThresholdRequest(
                                ThresholdStatus.APPROVED, "Approved"),
                        20L));
    }

    @Test
    void historyRejectsInvalidPagination() {
        assertAll(
                () -> assertThrows(InvalidRequestException.class,
                        () -> service.getHistory(100L, -1, 10)),
                () -> assertThrows(InvalidRequestException.class,
                        () -> service.getHistory(100L, 0, 0)),
                () -> assertThrows(InvalidRequestException.class,
                        () -> service.getHistory(100L, 0, 101))
        );

        verifyNoInteractions(repository, accessClient);
    }

    @Test
    void effectiveThresholdReturnsRepositorySelection() {
        ThresholdConfig approved = threshold(ThresholdStatus.APPROVED);
        LocalDateTime assessmentTime = EFFECTIVE_FROM.plusDays(1);

        when(repository.findLatestApprovedEffective(100L, assessmentTime))
                .thenReturn(Optional.of(approved));

        ThresholdConfig result =
                service.getEffectiveThreshold(100L, assessmentTime);

        assertEquals(approved, result);
        verify(accessClient).checkAccess(100L);
        verify(repository).findLatestApprovedEffective(100L, assessmentTime);
    }

    @Test
    void effectiveThresholdRejectsMissingApprovedConfiguration() {
        when(repository.findLatestApprovedEffective(100L, EFFECTIVE_FROM))
                .thenReturn(Optional.empty());

        assertThrows(InvalidRequestException.class,
                () -> service.getEffectiveThreshold(
                        100L, EFFECTIVE_FROM));
    }

    private CreateThresholdRequest createRequest() {
        return new CreateThresholdRequest(
                100L,
                new BigDecimal("15"),
                new BigDecimal("30"),
                new BigDecimal("70"),
                EFFECTIVE_FROM
        );
    }

    private ThresholdConfig threshold(ThresholdStatus status) {
        boolean reviewed = status != ThresholdStatus.PENDING;

        return new ThresholdConfig(
                1L,
                100L,
                2,
                new BigDecimal("15"),
                new BigDecimal("30"),
                new BigDecimal("70"),
                status,
                10L,
                EFFECTIVE_FROM.minusDays(1),
                reviewed ? 20L : null,
                reviewed ? EFFECTIVE_FROM : null,
                reviewed ? "Reviewed" : null,
                EFFECTIVE_FROM
        );
    }
}