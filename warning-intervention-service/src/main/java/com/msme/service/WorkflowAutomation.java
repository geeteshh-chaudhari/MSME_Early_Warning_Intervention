package com.msme.service;

import java.time.*;
import java.util.*;
import com.msme.entity.ActivityLog;
import com.msme.repository.ActivityLogRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable reconciliation uses existing entity IDs and ACTIVITY_LOG; no new schema. */
@Service
@ConditionalOnProperty(name="workflow.enabled",havingValue="true",matchIfMissing=true)
public class WorkflowAutomation {
    private final JdbcTemplate jdbc;private final ActivityLogRepository log;private final WarningService cases;
    private final TransactionTemplate tx;
    public WorkflowAutomation(JdbcTemplate jdbc,ActivityLogRepository log,WarningService cases,
            PlatformTransactionManager manager){this.jdbc=jdbc;this.log=log;this.cases=cases;tx=new TransactionTemplate(manager);}
    @Scheduled(fixedDelayString="${workflow.interval-ms:5000}",initialDelay=5000)
    public void reconcile(){
        try {
            for(var row:jdbc.queryForList("SELECT s.snapshot_id,s.msme_id,s.reporting_period FROM financial_snapshot s JOIN msme m ON m.msme_id=s.msme_id WHERE s.revenue=0 AND m.is_active='Y' AND NOT EXISTS (SELECT 1 FROM activity_log l WHERE l.related_entity_type='FINANCIAL_SNAPSHOT' AND l.related_entity_id=s.snapshot_id AND l.action_code='ZERO_REVENUE_SUBMITTED') FETCH FIRST 100 ROWS ONLY"))
                event(number(row,"MSME_ID"),"FINANCIAL_SNAPSHOT",number(row,"SNAPSHOT_ID"),"ZERO_REVENUE_SUBMITTED","URGENT: zero revenue submitted for "+row.get("REPORTING_PERIOD")+". Assigned RM: take an immediate decision; full assessment may still be awaiting history.");
            for(var row:jdbc.queryForList("SELECT a.assessment_id,a.msme_id,s.reporting_period FROM risk_assessment a JOIN financial_snapshot s ON s.snapshot_id=a.snapshot_id AND s.msme_id=a.msme_id WHERE s.revenue=0 AND NOT EXISTS (SELECT 1 FROM activity_log l WHERE l.related_entity_type='RISK_ASSESSMENT' AND l.related_entity_id=a.assessment_id AND l.action_code='ZERO_REVENUE_ASSESSED') FETCH FIRST 100 ROWS ONLY"))
                event(number(row,"MSME_ID"),"RISK_ASSESSMENT",number(row,"ASSESSMENT_ID"),"ZERO_REVENUE_ASSESSED","URGENT: zero revenue assessed for "+row.get("REPORTING_PERIOD")+". RM and Risk Admin: immediate review required.");
            for(var row:jdbc.queryForList("SELECT a.assessment_id,a.msme_id,a.severity,s.reporting_period FROM risk_assessment a JOIN financial_snapshot s ON s.snapshot_id=a.snapshot_id AND s.msme_id=a.msme_id WHERE NOT EXISTS (SELECT 1 FROM activity_log l WHERE l.related_entity_type='RISK_ASSESSMENT' AND l.related_entity_id=a.assessment_id AND l.action_code='ASSESSMENT_COMPLETED') FETCH FIRST 100 ROWS ONLY"))
                event(number(row,"MSME_ID"),"RISK_ASSESSMENT",number(row,"ASSESSMENT_ID"),"ASSESSMENT_COMPLETED","Assessment completed for "+row.get("REPORTING_PERIOD")+": "+row.get("SEVERITY")+".");
            for(var id:jdbc.queryForList("SELECT m.msme_id FROM msme m WHERE m.is_active='Y' AND NOT EXISTS (SELECT 1 FROM activity_log l WHERE l.related_entity_type='MSME' AND l.related_entity_id=m.msme_id AND l.action_code='MSME_REGISTERED') FETCH FIRST 100 ROWS ONLY",Long.class))
                event(id,"MSME",id,"MSME_REGISTERED","New MSME registered. Risk Admin: configure thresholds immediately.");
            for(var row:jdbc.queryForList("SELECT t.threshold_config_id,t.msme_id FROM threshold_config t WHERE NOT EXISTS (SELECT 1 FROM activity_log l WHERE l.related_entity_type='THRESHOLD_CONFIG' AND l.related_entity_id=t.threshold_config_id AND l.action_code='CONFIG_CREATED') FETCH FIRST 100 ROWS ONLY"))
                event(number(row,"MSME_ID"),"THRESHOLD_CONFIG",number(row,"THRESHOLD_CONFIG_ID"),"CONFIG_CREATED","Risk Admin saved a threshold configuration version.");
        } catch(Exception ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Case reconciliation pending ({})",ex.getClass().getSimpleName());}
    }
    private static Long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    private void event(Long msme,String type,Long id,String action,String text){
        tx.executeWithoutResult(status->{
            jdbc.queryForObject("SELECT msme_id FROM msme WHERE msme_id=? FOR UPDATE",Long.class,msme);
            if(!log.existsByRelatedEntityTypeAndRelatedEntityIdAndActionCode(type,id,action))save(msme,type,id,action,text);
        });
    }
    private void save(Long msme,String type,Long id,String action,String text){
        var e=new ActivityLog();e.setMsmeId(msme);e.setActorType("SYSTEM");e.setRelatedEntityType(type);e.setRelatedEntityId(id);e.setActionCode(action);e.setDescription(text);e.setCreatedAt(LocalDateTime.now());e.setMetadataJson("{}");log.save(e);
    }
}
