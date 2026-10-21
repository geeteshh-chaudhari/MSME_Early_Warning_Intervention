package com.msme.service;

import java.time.*;
import java.util.*;
import com.msme.calculator.AssessmentCalculator;
import com.msme.dto.assessment.AssessmentInputResponse;
import com.msme.entity.*;
import com.msme.repository.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Reconciles persisted snapshots. No browser can supply assessment amounts. */
@Service
@ConditionalOnProperty(name="workflow.enabled",havingValue="true",matchIfMissing=true)
public class AutomaticAssessmentService {
    public record ReadyPeriod(Long msmeId, LocalDate reportingPeriod) {}
    private final RestClient finance;
    private final AssessmentCalculator calculator;
    private final ThresholdConfigRepository thresholds;
    private final RiskAssessmentRepository assessments;
    private final AssessmentIndicatorResultRepository indicators;
    private final TransactionTemplate tx;
    private final JdbcTemplate jdbc;
    public AutomaticAssessmentService(@Value("${msme.finance.base-url}") String url,
            @Value("${app.internal.token:}") String token, AssessmentCalculator calculator,
            ThresholdConfigRepository thresholds, RiskAssessmentRepository assessments,
            AssessmentIndicatorResultRepository indicators, PlatformTransactionManager manager, JdbcTemplate jdbc) {
        var factory=new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        finance=RestClient.builder().baseUrl(url).defaultHeader("X-Internal-Token",token).requestFactory(factory).build();
        this.calculator=calculator;this.thresholds=thresholds;this.assessments=assessments;this.indicators=indicators;this.jdbc=jdbc;tx=new TransactionTemplate(manager);
    }
    @Scheduled(fixedDelayString="${workflow.interval-ms:10000}",initialDelay=3000)
    public void reconcile() {
        try {
            var ready=finance.get().uri("/internal/finance/ready").retrieve().body(new ParameterizedTypeReference<List<ReadyPeriod>>(){});
            if(ready==null)return;
            int processed=0;
            for(var row:ready) {
                if(processed>=100)break;
                try {
                    var ids=jdbc.queryForList("SELECT a.assessment_id FROM risk_assessment a JOIN financial_snapshot s ON s.snapshot_id=a.snapshot_id WHERE a.msme_id=? AND s.reporting_period=?",Long.class,row.msmeId(),java.sql.Date.valueOf(row.reportingPeriod()));
                    if(!ids.isEmpty())continue;
                    if(thresholds.findLatestApprovedEffective(row.msmeId(),LocalDateTime.now()).isEmpty())continue;
                    process(row);processed++;
                } catch(Exception ex) {
                    LoggerFactory.getLogger(getClass()).warn("Assessment pending for MSME {} period {} ({})",row.msmeId(),row.reportingPeriod(),ex.getClass().getSimpleName());
                }
            }
        } catch(Exception ex) {
            LoggerFactory.getLogger(getClass()).warn("Finance reconciliation unavailable ({})",ex.getClass().getSimpleName());
        }
    }
    void process(ReadyPeriod row) {
        var input=finance.get().uri("/internal/finance/{id}/{period}",row.msmeId(),row.reportingPeriod()).retrieve().body(AssessmentInputResponse.class);
        if(input==null || !row.msmeId().equals(input.msmeId()) || !row.reportingPeriod().equals(input.reportingPeriod()) || input.snapshots()==null)
            throw new IllegalArgumentException("Invalid finance response");
        var snapshots=input.snapshots().stream().map(s->new AssessmentCalculator.Snapshot(s.snapshotId(),s.msmeId(),s.reportingPeriod(),s.revenue(),s.totalReceivables(),s.overdueReceivables(),s.creditUsed(),input.sanctionedCreditLimit())).toList();
        var current=snapshots.stream().max(Comparator.comparing(AssessmentCalculator.Snapshot::reportingPeriod)).orElseThrow();
        if(!current.reportingPeriod().equals(row.reportingPeriod()) || snapshots.stream().anyMatch(s->!row.msmeId().equals(s.msmeId())))
            throw new IllegalArgumentException("Finance evidence does not match requested MSME and reporting period");
        tx.executeWithoutResult(status->{
            // Same MSME lock serializes config versions, assessment creation and backfill links.
            String active=jdbc.queryForObject("SELECT is_active FROM msme WHERE msme_id=? FOR UPDATE",String.class,row.msmeId());
            if(!"Y".equals(active))throw new IllegalArgumentException("Inactive MSME");
            if(assessments.findBySnapshotId(current.snapshotId()).isPresent())return;
            var threshold=thresholds.findLatestApprovedEffective(row.msmeId(),LocalDateTime.now()).orElseThrow();
            var result=calculator.calculate(snapshots,new AssessmentCalculator.Thresholds(threshold.revenueDeclinePct(),threshold.overdueReceivablePct(),threshold.creditUtilisationPct()));
            Long id=assessments.save(new RiskAssessment(null,row.msmeId(),current.snapshotId(),threshold.thresholdConfigId(),null,result.severity(),result.stressPattern(),null,null));
            indicators.saveAll(result.indicators().stream().map(i->new AssessmentIndicatorResult(null,id,i.indicatorCode(),i.calculatedValue(),i.thresholdValue(),i.breached(),i.explanation())).toList());
            var ordered=jdbc.queryForList("SELECT a.assessment_id FROM risk_assessment a JOIN financial_snapshot s ON s.snapshot_id=a.snapshot_id WHERE a.msme_id=? ORDER BY s.reporting_period,a.assessment_id",Long.class,row.msmeId());
            Long previous=null;
            for(Long assessmentId:ordered){jdbc.update("UPDATE risk_assessment SET previous_assessment_id=? WHERE assessment_id=?",previous,assessmentId);previous=assessmentId;}
        });
    }
}
