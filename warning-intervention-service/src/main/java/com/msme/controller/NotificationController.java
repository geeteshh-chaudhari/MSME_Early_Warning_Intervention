package com.msme.controller;
import java.util.*;
import com.msme.service.CaseAccessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final JdbcTemplate jdbc;private final CaseAccessService access;
    public NotificationController(JdbcTemplate jdbc,CaseAccessService access){this.jdbc=jdbc;this.access=access;}
    @GetMapping public List<Map<String,Object>> list(@RequestParam(defaultValue="0") long after){
        var u=access.currentUser();
        String scope=switch(u.getRole()) {case "RISK_ADMIN"->"1=1";case "RM"->"m.assigned_rm_id="+u.getUserId();case "MSME_REP"->"m.msme_id="+Objects.requireNonNullElse(u.getMsmeId(),-1L);default->"1=0";};
        String events="'CONFIG_CREATED','ASSESSMENT_COMPLETED','WARNING_CREATED','RECOMMENDATION_GENERATED','INTERVENTION_CREATED','CLOSE','REJECT'";
        if("RM".equals(u.getRole()) || "RISK_ADMIN".equals(u.getRole()))events+=",'ZERO_REVENUE_ASSESSED'";
        if("RM".equals(u.getRole()))events+=",'ZERO_REVENUE_SUBMITTED'";
        if("RISK_ADMIN".equals(u.getRole()))events+=",'MSME_REGISTERED','ESCALATE'";
        return jdbc.query("SELECT l.activity_id,l.msme_id,m.business_name,l.action_code,l.description,l.created_at FROM activity_log l JOIN msme m ON m.msme_id=l.msme_id WHERE "+scope+" AND l.activity_id>? AND l.action_code IN ("+events+") ORDER BY l.activity_id DESC FETCH FIRST 100 ROWS ONLY",
            (rs,n)->{Map<String,Object> row=new LinkedHashMap<>();row.put("id",rs.getLong(1));row.put("msmeId",rs.getLong(2));row.put("businessName",rs.getString(3));row.put("event",rs.getString(4));row.put("message",rs.getString(5));row.put("createdAt",rs.getTimestamp(6).toLocalDateTime());return row;},Math.max(0,after));
    }
}
