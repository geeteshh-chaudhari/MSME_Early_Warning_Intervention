package com.msme.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.List;
import com.msme.dto.snapshot.AssessmentInputResponse;
import com.msme.dto.snapshot.FinancialSnapshotResponse;
import com.msme.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Private MS1 contract: never routed through the browser gateway. */
@RestController
@io.swagger.v3.oas.annotations.Hidden
@RequestMapping("/internal/finance")
public class InternalFinanceController {
    private final String token;
    private final JdbcTemplate jdbc;
    private final MsmeRepository msmes;
    private final FinancialSnapshotRepository snapshots;
    public InternalFinanceController(@Value("${app.internal.token:}") String token, JdbcTemplate jdbc,
            MsmeRepository msmes, FinancialSnapshotRepository snapshots) {
        this.token=token; this.jdbc=jdbc; this.msmes=msmes; this.snapshots=snapshots;
    }
    private void authenticate(String supplied) {
        if (token.length()<32 || supplied==null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    public record ReadyPeriod(Long msmeId, LocalDate reportingPeriod) {}
    @GetMapping("/ready")
    @Transactional(readOnly=true)
    public List<ReadyPeriod> ready(@RequestHeader(value="X-Internal-Token",required=false) String supplied) {
        authenticate(supplied);
        return jdbc.query("""
            SELECT s.msme_id, s.reporting_period FROM financial_snapshot s
            JOIN msme m ON m.msme_id=s.msme_id AND m.is_active='Y'
            WHERE s.reporting_period < ?
            AND EXISTS (SELECT 1 FROM financial_snapshot p WHERE p.msme_id=s.msme_id AND p.reporting_period=ADD_MONTHS(s.reporting_period,-1))
            AND EXISTS (SELECT 1 FROM financial_snapshot p WHERE p.msme_id=s.msme_id AND p.reporting_period=ADD_MONTHS(s.reporting_period,-2))
            ORDER BY s.reporting_period, s.msme_id
            """, (rs,n)->new ReadyPeriod(rs.getLong(1),rs.getDate(2).toLocalDate()),
            java.sql.Date.valueOf(LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).withDayOfMonth(1)));
    }
    @GetMapping("/{id}/{period}")
    @Transactional(readOnly=true)
    public AssessmentInputResponse input(@RequestHeader(value="X-Internal-Token",required=false) String supplied,
            @PathVariable Long id, @PathVariable LocalDate period) {
        authenticate(supplied);
        var m=msmes.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!"Y".equals(m.getIsActive()) || period.getDayOfMonth()!=1 || !period.isBefore(LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).withDayOfMonth(1)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Inactive MSME or invalid reporting period");
        var rows=java.util.stream.IntStream.rangeClosed(0,2).mapToObj(i-> {
            var s=snapshots.findByMsmeIdAndReportingPeriod(id,period.minusMonths(2-i))
                .orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Missing monthly history"));
            return new FinancialSnapshotResponse(s.getSnapshotId(),s.getMsmeId(),s.getReportingPeriod(),s.getRevenue(),
                s.getTotalReceivables(),s.getOverdueReceivables(),s.getCreditUsed(),s.getSubmittedByUserId(),s.getSubmittedAt(),s.getCreatedAt(),s.getUpdatedAt());
        }).toList();
        return new AssessmentInputResponse(id,m.getBusinessName(),m.getSanctionedCreditLimit(),period,rows);
    }
}
