package com.msme.service;

import com.msme.dto.snapshot.CreateFinancialSnapshotRequest;
import com.msme.dto.snapshot.FinancialSnapshotResponse;
import com.msme.dto.snapshot.SnapshotReadinessResponse;
import com.msme.entity.AppUser;
import com.msme.entity.FinancialSnapshot;
import com.msme.entity.Msme;
import com.msme.exception.DuplicateResourceException;
import com.msme.exception.ForbiddenOperationException;
import com.msme.exception.InvalidRequestException;
import com.msme.exception.ResourceNotFoundException;
import com.msme.repository.FinancialSnapshotRepository;
import com.msme.repository.MsmeRepository;
import com.msme.security.CustomUserPrincipal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinancialSnapshotServiceTest {

    private static final LocalDate PERIOD =
            LocalDate.of(2026, 9, 1);

    @Mock
    private FinancialSnapshotRepository snapshotRepository;

    @Mock
    private MsmeRepository msmeRepository;

    @InjectMocks
    private FinancialSnapshotService service;

    private Msme msme;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        msme = new Msme();
        msme.setMsmeId(100L);
        msme.setBusinessName("ABC Industries");
        msme.setAssignedRmId(10L);
        msme.setIsActive("Y");
        msme.setSanctionedCreditLimit(new BigDecimal("500000.00"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createSnapshotStoresFinancialDataAndOwnership() {
        login(20L, "MSME_REP", 100L);
        existingMsme();

        when(snapshotRepository.save(any(FinancialSnapshot.class)))
                .thenAnswer(invocation -> {
                    FinancialSnapshot saved = invocation.getArgument(0);
                    saved.setSnapshotId(1L);
                    return saved;
                });

        FinancialSnapshotResponse result =
                service.createSnapshot(validRequest());

        assertAll(
                () -> assertEquals(1L, result.getSnapshotId()),
                () -> assertEquals(100L, result.getMsmeId()),
                () -> assertEquals(20L, result.getSubmittedByUserId()),
                () -> assertEquals(PERIOD, result.getReportingPeriod()),
                () -> assertEquals(new BigDecimal("100000.00"),
                        result.getRevenue()),
                () -> assertEquals(new BigDecimal("50000.00"),
                        result.getTotalReceivables()),
                () -> assertEquals(new BigDecimal("10000.00"),
                        result.getOverdueReceivables()),
                () -> assertEquals(new BigDecimal("200000.00"),
                        result.getCreditUsed()),
                () -> assertNotNull(result.getSubmittedAt()),
                () -> assertNotNull(result.getCreatedAt()),
                () -> assertNotNull(result.getUpdatedAt())
        );

        verify(snapshotRepository).save(any(FinancialSnapshot.class));
    }

    @Test
    void createSnapshotRejectsMissingAuthentication() {
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> service.createSnapshot(validRequest()));

        verifyNoInteractions(msmeRepository, snapshotRepository);
    }

    @Test
    void createSnapshotRejectsRepresentativeWithoutMsme() {
        login(20L, "MSME_REP", null);

        assertThrows(ForbiddenOperationException.class,
                () -> service.createSnapshot(validRequest()));

        verifyNoInteractions(msmeRepository, snapshotRepository);
    }

    @Test
    void createSnapshotRejectsMissingMsme() {
        login(20L, "MSME_REP", 100L);
        when(msmeRepository.findById(100L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.createSnapshot(validRequest()));

        verifyNoInteractions(snapshotRepository);
    }

    @Test
    void createSnapshotRejectsInactiveMsme() {
        login(20L, "MSME_REP", 100L);
        msme.setIsActive("N");
        existingMsme();

        assertThrows(InvalidRequestException.class,
                () -> service.createSnapshot(validRequest()));

        verifyNoInteractions(snapshotRepository);
    }

    @Test
    void createSnapshotRejectsDuplicateMonth() {
        login(20L, "MSME_REP", 100L);
        existingMsme();

        when(snapshotRepository.existsByMsmeIdAndReportingPeriod(
                100L, PERIOD)).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> service.createSnapshot(validRequest()));

        verify(snapshotRepository, never())
                .save(any(FinancialSnapshot.class));
    }

    @Test
    void createSnapshotRejectsPeriodThatIsNotFirstDay() {
        login(20L, "MSME_REP", 100L);
        existingMsme();

        CreateFinancialSnapshotRequest request = validRequest();
        request.setReportingPeriod(LocalDate.of(2026, 9, 15));

        assertThrows(InvalidRequestException.class,
                () -> service.createSnapshot(request));

        verifyNoInteractions(snapshotRepository);
    }

    @Test
    void createSnapshotRejectsMissingPeriod() {
        login(20L, "MSME_REP", 100L);
        existingMsme();

        CreateFinancialSnapshotRequest request = validRequest();
        request.setReportingPeriod(null);

        assertThrows(InvalidRequestException.class,
                () -> service.createSnapshot(request));

        verifyNoInteractions(snapshotRepository);
    }

    @Test
    void createSnapshotRejectsOverdueAboveTotalReceivables() {
        login(20L, "MSME_REP", 100L);
        existingMsme();

        CreateFinancialSnapshotRequest request = validRequest();
        request.setOverdueReceivables(new BigDecimal("60000.00"));

        assertThrows(InvalidRequestException.class,
                () -> service.createSnapshot(request));

        verifyNoInteractions(snapshotRepository);
    }

    @Test
    void representativeCanReadOwnHistory() {
        login(20L, "MSME_REP", 100L);
        existingMsme();

        FinancialSnapshot snapshot = new FinancialSnapshot();
        snapshot.setSnapshotId(1L);
        snapshot.setMsmeId(100L);
        snapshot.setReportingPeriod(PERIOD);

        when(snapshotRepository.findByMsmeIdOrderByReportingPeriodAsc(100L))
                .thenReturn(List.of(snapshot));

        List<FinancialSnapshotResponse> results = service.getMySnapshots();

        assertEquals(1, results.size());
        assertEquals(100L, results.get(0).getMsmeId());
        assertEquals(PERIOD, results.get(0).getReportingPeriod());
    }

    @Test
    void rmCannotReadAnotherRmsFinancialHistory() {
        login(99L, "RM", null);
        existingMsme();

        assertThrows(ForbiddenOperationException.class,
                () -> service.getSnapshotsByMsme(100L));

        verifyNoInteractions(snapshotRepository);
    }

    @Test
    void readinessIsTrueWhenAllThreeMonthsExist() {
        login(20L, "MSME_REP", 100L);
        existingMsme();

        when(snapshotRepository.existsByMsmeIdAndReportingPeriod(
                100L, PERIOD)).thenReturn(true);
        when(snapshotRepository.existsByMsmeIdAndReportingPeriod(
                100L, PERIOD.minusMonths(1))).thenReturn(true);
        when(snapshotRepository.existsByMsmeIdAndReportingPeriod(
                100L, PERIOD.minusMonths(2))).thenReturn(true);

        SnapshotReadinessResponse result = service.checkReadiness(PERIOD);

        assertTrue(result.isAssessmentReady());
        assertTrue(result.isCurrentPeriodAvailable());
        assertTrue(result.isPreviousPeriodAvailable());
        assertTrue(result.isTwoPeriodsPriorAvailable());
        assertTrue(result.getMissingPeriods().isEmpty());
    }

    @Test
    void readinessListsMissingMonths() {
        login(20L, "MSME_REP", 100L);
        existingMsme();

        // Unstubbed boolean repository calls return false.
        when(snapshotRepository.existsByMsmeIdAndReportingPeriod(
                100L, PERIOD)).thenReturn(true);

        SnapshotReadinessResponse result = service.checkReadiness(PERIOD);

        assertFalse(result.isAssessmentReady());
        assertEquals(List.of("2026-07", "2026-08"),
                result.getMissingPeriods());
    }

    @Test
    void representativeCannotRequestAnotherMsmesAssessmentData() {
        login(20L, "MSME_REP", 100L);

        assertThrows(ForbiddenOperationException.class,
                () -> service.getAssessmentInput(200L, PERIOD));

        verifyNoInteractions(msmeRepository, snapshotRepository);
    }

    @Test
    void assessmentInputRejectsMissingFinancialMonth() {
        login(10L, "RM", null);
        existingMsme();

        when(snapshotRepository.findByMsmeIdAndReportingPeriod(
                100L, PERIOD)).thenReturn(Optional.empty());

        InvalidRequestException exception =
                assertThrows(InvalidRequestException.class,
                        () -> service.getAssessmentInput(100L, PERIOD));

        assertTrue(exception.getMessage().contains("2026-09"));
    }

    private void existingMsme() {
        when(msmeRepository.findById(100L))
                .thenReturn(Optional.of(msme));
    }

    private CreateFinancialSnapshotRequest validRequest() {
        CreateFinancialSnapshotRequest request =
                new CreateFinancialSnapshotRequest();

        request.setReportingPeriod(PERIOD);
        request.setRevenue(new BigDecimal("100000.00"));
        request.setTotalReceivables(new BigDecimal("50000.00"));
        request.setOverdueReceivables(new BigDecimal("10000.00"));
        request.setCreditUsed(new BigDecimal("200000.00"));

        return request;
    }

    private void login(Long userId, String role, Long msmeId) {
        AppUser user = new AppUser();
        user.setUserId(userId);
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setPasswordHash("test-hash");
        user.setRole(role);
        user.setMsmeId(msmeId);
        user.setAccountStatus("ACTIVE");

        CustomUserPrincipal principal = new CustomUserPrincipal(user);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()
                )
        );
    }
}