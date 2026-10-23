
package com.msme.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.msme.entity.RiskAssessment;
import com.msme.enums.Severity;
import com.msme.enums.StressPattern;

@Repository
public class RiskAssessmentRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public RiskAssessmentRepository(
            NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<RiskAssessment> rowMapper =
            (rs, rowNum) -> {

                String pattern = rs.getString("STRESS_PATTERN");

                return new RiskAssessment(
                    rs.getLong("ASSESSMENT_ID"),
                    rs.getLong("MSME_ID"),
                    rs.getLong("SNAPSHOT_ID"),
                    rs.getLong("THRESHOLD_CONFIG_ID"),
                    rs.getTimestamp("ASSESSMENT_DATE")
                        .toLocalDateTime(),
                    Severity.valueOf(
                        rs.getString("SEVERITY")
                    ),
                    pattern == null
                        ? null
                        : StressPattern.valueOf(pattern),
                    rs.getObject(
                        "PREVIOUS_ASSESSMENT_ID",
                        Long.class
                    ),
                    rs.getTimestamp("CREATED_AT")
                        .toLocalDateTime()
                );
            };

    public Optional<RiskAssessment> findById(Long id) {

        String sql = """
            SELECT *
            FROM RISK_ASSESSMENT
            WHERE ASSESSMENT_ID = :id
            """;

        return jdbc.query(
                sql,
                new MapSqlParameterSource("id", id),
                rowMapper
        ).stream().findFirst();
    }

    public Optional<RiskAssessment> findBySnapshotId(
            Long snapshotId) {

        String sql = """
            SELECT *
            FROM RISK_ASSESSMENT
            WHERE SNAPSHOT_ID = :snapshotId
            """;

        return jdbc.query(
                sql,
                new MapSqlParameterSource(
                    "snapshotId", snapshotId),
                rowMapper
        ).stream().findFirst();
    }

    public Optional<RiskAssessment> findLatestByMsmeId(
            Long msmeId) {

        String sql = """
            SELECT a.*
            FROM RISK_ASSESSMENT a
            JOIN FINANCIAL_SNAPSHOT s ON s.SNAPSHOT_ID = a.SNAPSHOT_ID AND s.MSME_ID = a.MSME_ID
            WHERE a.MSME_ID = :msmeId
            ORDER BY s.REPORTING_PERIOD DESC,
                     a.ASSESSMENT_ID DESC
            FETCH FIRST 1 ROW ONLY
            """;

        return jdbc.query(
                sql,
                new MapSqlParameterSource("msmeId", msmeId),
                rowMapper
        ).stream().findFirst();
    }

    public List<RiskAssessment> findHistory(
            Long msmeId,
            int page,
            int size) {

        String sql = """
            SELECT a.*
            FROM RISK_ASSESSMENT a
            JOIN FINANCIAL_SNAPSHOT s ON s.SNAPSHOT_ID = a.SNAPSHOT_ID AND s.MSME_ID = a.MSME_ID
            WHERE a.MSME_ID = :msmeId
            ORDER BY s.REPORTING_PERIOD DESC,
                     a.ASSESSMENT_ID DESC
            OFFSET :offset ROWS
            FETCH NEXT :size ROWS ONLY
            """;

        return jdbc.query(
                sql,
                new MapSqlParameterSource()
                    .addValue("msmeId", msmeId)
                    .addValue("offset", page * size)
                    .addValue("size", size),
                rowMapper
        );
    }

    public long countByMsmeId(Long msmeId) {

        String sql = """
            SELECT COUNT(*)
            FROM RISK_ASSESSMENT
            WHERE MSME_ID = :msmeId
            """;

        Long count = jdbc.queryForObject(
                sql,
                new MapSqlParameterSource("msmeId", msmeId),
                Long.class
        );

        return count == null ? 0 : count;
    }

    public java.time.LocalDate reportingPeriod(Long assessmentId) {
        return jdbc.queryForObject("""
                SELECT s.REPORTING_PERIOD FROM RISK_ASSESSMENT a
                JOIN FINANCIAL_SNAPSHOT s ON s.SNAPSHOT_ID=a.SNAPSHOT_ID AND s.MSME_ID=a.MSME_ID
                WHERE a.ASSESSMENT_ID=:id
                """, new MapSqlParameterSource("id", assessmentId), java.sql.Date.class).toLocalDate();
    }

    public void lockMsme(Long msmeId) {
        jdbc.queryForObject("SELECT MSME_ID FROM MSME WHERE MSME_ID=:id FOR UPDATE",
                new MapSqlParameterSource("id", msmeId), Long.class);
    }

    /** Caller holds the MSME row lock. Repair pointers only, never calculation results. */
    public void repairChronology(Long msmeId) {
        var ordered = jdbc.query("""
                SELECT a.* FROM RISK_ASSESSMENT a
                JOIN FINANCIAL_SNAPSHOT s ON s.SNAPSHOT_ID=a.SNAPSHOT_ID AND s.MSME_ID=a.MSME_ID
                WHERE a.MSME_ID=:id ORDER BY s.REPORTING_PERIOD, a.ASSESSMENT_ID
                """, new MapSqlParameterSource("id", msmeId), rowMapper);
        Long previous = null;
        for (var assessment : ordered) {
            if (!java.util.Objects.equals(previous, assessment.previousAssessmentId())) {
                jdbc.update("UPDATE RISK_ASSESSMENT SET PREVIOUS_ASSESSMENT_ID=:previous WHERE ASSESSMENT_ID=:id AND MSME_ID=:msmeId",
                        new MapSqlParameterSource("previous", previous).addValue("id", assessment.assessmentId())
                                .addValue("msmeId", msmeId));
            }
            previous = assessment.assessmentId();
        }
    }

    public Long save(RiskAssessment assessment) {

        String sql = """
            INSERT INTO RISK_ASSESSMENT (
                MSME_ID,
                SNAPSHOT_ID,
                THRESHOLD_CONFIG_ID,
                SEVERITY,
                STRESS_PATTERN,
                PREVIOUS_ASSESSMENT_ID
            )
            VALUES (
                :msmeId,
                :snapshotId,
                :thresholdId,
                :severity,
                :stressPattern,
                :previousId
            )
            """;

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                    .addValue("msmeId", assessment.msmeId())
                    .addValue(
                        "snapshotId", assessment.snapshotId())
                    .addValue(
                        "thresholdId",
                        assessment.thresholdConfigId())
                    .addValue(
                        "severity",
                        assessment.severity().name())
                    .addValue(
                        "stressPattern",
                        assessment.stressPattern() == null
                            ? null
                            : assessment.stressPattern().name())
                    .addValue(
                        "previousId",
                        assessment.previousAssessmentId());

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbc.update(
                sql,
                params,
                keyHolder,
                new String[]{"ASSESSMENT_ID"}
        );

        Number key = keyHolder.getKey();

        if (key == null) {
            throw new IllegalStateException(
                    "Assessment ID was not generated");
        }

        return key.longValue();
    }
}
