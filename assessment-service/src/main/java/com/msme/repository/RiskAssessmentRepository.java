
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
            SELECT *
            FROM RISK_ASSESSMENT
            WHERE MSME_ID = :msmeId
            ORDER BY ASSESSMENT_DATE DESC,
                     ASSESSMENT_ID DESC
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
            SELECT *
            FROM RISK_ASSESSMENT
            WHERE MSME_ID = :msmeId
            ORDER BY ASSESSMENT_DATE DESC,
                     ASSESSMENT_ID DESC
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
