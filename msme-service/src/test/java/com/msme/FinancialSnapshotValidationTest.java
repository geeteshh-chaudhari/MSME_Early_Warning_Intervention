package com.msme;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import com.msme.dto.snapshot.CreateFinancialSnapshotRequest;
import com.msme.entity.*;
import com.msme.repository.*;
import com.msme.security.CustomUserPrincipal;
import com.msme.service.FinancialSnapshotService;
import com.msme.exception.InvalidRequestException;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialSnapshotValidationTest {
    private final FinancialSnapshotRepository snapshots=mock(FinancialSnapshotRepository.class);
    private final MsmeRepository businesses=mock(MsmeRepository.class);
    private final FinancialSnapshotService service=new FinancialSnapshotService(snapshots,businesses);
    private final LocalDate month=LocalDate.now(ZoneId.of("Asia/Kolkata")).withDayOfMonth(1);
    @BeforeEach void authenticateRepresentative() {
        var u=new AppUser();u.setUserId(7L);u.setMsmeId(1L);u.setRole("MSME_REP");u.setAccountStatus("ACTIVE");
        var principal=new CustomUserPrincipal(u);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        var m=new Msme();m.setMsmeId(1L);m.setIsActive("Y");when(businesses.findById(1L)).thenReturn(Optional.of(m));
        when(snapshots.save(any())).thenAnswer(i->i.getArgument(0));
    }
    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }
    private CreateFinancialSnapshotRequest request(LocalDate period) {
        return new CreateFinancialSnapshotRequest(period,new BigDecimal("100"),new BigDecimal("50"),BigDecimal.ZERO,BigDecimal.ZERO);
    }
    @Test void currentAndFutureMonthsAreRejectedBeforeSaving() {
        assertThrows(InvalidRequestException.class,()->service.createSnapshot(request(month)));
        assertThrows(InvalidRequestException.class,()->service.createSnapshot(request(month.plusMonths(1))));
        verify(snapshots,never()).save(any());
    }
    @Test void completedBackfillIsAcceptedForAuthenticatedBusiness() {
        var result=service.createSnapshot(request(month.minusMonths(5)));
        assertEquals(1L,result.getMsmeId());
        assertEquals(month.minusMonths(5),result.getReportingPeriod());
    }
    @Test void overdueCannotExceedTotalReceivables() {
        var request=request(month.minusMonths(1));request.setOverdueReceivables(new BigDecimal("51"));
        assertThrows(InvalidRequestException.class,()->service.createSnapshot(request));
        verify(snapshots,never()).save(any());
    }
}
