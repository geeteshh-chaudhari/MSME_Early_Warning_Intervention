package com.msme;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import com.msme.entity.RiskAssessment;
import com.msme.enums.Severity;
import com.msme.repository.RiskAssessmentRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ReportingChronologyTest {
    @Test void backfillRepairsPreviousPointersInReportingOrderOnly() {
        var jdbc=mock(NamedParameterJdbcTemplate.class);
        var repository=new RiskAssessmentRepository(jdbc);
        // September was created first; August was backfilled later, with a wrong historic pointer.
        var august=new RiskAssessment(20L,1L,2L,1L,LocalDateTime.now(),Severity.HEALTHY,null,999L,LocalDateTime.now());
        var september=new RiskAssessment(10L,1L,3L,1L,LocalDateTime.now().minusDays(1),Severity.WATCH,null,null,LocalDateTime.now());
        when(jdbc.query(contains("ORDER BY s.REPORTING_PERIOD"),any(SqlParameterSource.class),org.mockito.ArgumentMatchers.<RowMapper<RiskAssessment>>any()))
                .thenReturn(List.of(august,september));
        repository.repairChronology(1L);
        var parameters=org.mockito.ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc,times(2)).update(startsWith("UPDATE RISK_ASSESSMENT SET PREVIOUS_ASSESSMENT_ID"),parameters.capture());
        assertNull(parameters.getAllValues().get(0).getValue("previous"));
        assertEquals(20L,parameters.getAllValues().get(0).getValue("id"));
        assertEquals(20L,parameters.getAllValues().get(1).getValue("previous"));
        assertEquals(10L,parameters.getAllValues().get(1).getValue("id"));
        assertTrue(parameters.getAllValues().stream().allMatch(p->Long.valueOf(1L).equals(p.getValue("msmeId"))));
    }
}
