
package com.msme.repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.msme.entity.AssessmentIndicatorResult;
import com.msme.enums.IndicatorCode;

@Repository
public class AssessmentIndicatorResultRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AssessmentIndicatorResultRepository(
            NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<AssessmentIndicatorResult> rowMapper =
            (rs, rowNum) -> {

                String flag = rs.getString("BREACHED_FLAG");

                if (!"Y".equals(flag) && !"N".equals(flag)) {
                    throw new IllegalStateException(
                        "Invalid BREACHED_FLAG stored in Oracle"
                    );
                }

                return new AssessmentIndicatorResult(
                    rs.getLong("INDICATOR_RESULT_ID"),
                    rs.getLong("ASSESSMENT_ID"),
                    IndicatorCode.valueOf(
                        rs.getString("INDICATOR_CODE")
                    ),
                    rs.getBigDecimal("CALCULATED_VALUE"),
                    rs.getBigDecimal("THRESHOLD_VALUE_USED"),
                    "Y".equals(flag),
                    rs.getString("EXPLANATION_TEXT")
                );
            };

    public List<AssessmentIndicatorResult> findByAssessmentId(
            Long assessmentId) {

        String sql = """
            SELECT *
            FROM ASSESSMENT_INDICATOR_RESULT
            WHERE ASSESSMENT_ID = :assessmentId
            ORDER BY INDICATOR_RESULT_ID
            """;

        return jdbc.query(
                sql,
                new MapSqlParameterSource(
                    "assessmentId", assessmentId),
                rowMapper
        );
    }

    public int save(AssessmentIndicatorResult result) {

        String sql = """
            INSERT INTO ASSESSMENT_INDICATOR_RESULT (
                ASSESSMENT_ID,
                INDICATOR_CODE,
                CALCULATED_VALUE,
                THRESHOLD_VALUE_USED,
                BREACHED_FLAG,
                EXPLANATION_TEXT
            )
            VALUES (
                :assessmentId,
                :indicatorCode,
                :calculatedValue,
                :thresholdValue,
                :breachedFlag,
                :explanation
            )
            """;

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                    .addValue(
                        "assessmentId", result.assessmentId())
                    .addValue(
                        "indicatorCode",
                        result.indicatorCode().name())
                    .addValue(
                        "calculatedValue",
                        roundForStorage(result.calculatedValue()))
                    .addValue(
                        "thresholdValue",
                        roundForStorage(result.thresholdValueUsed()))
                    .addValue(
                        "breachedFlag",
                        result.breached() ? "Y" : "N")
                    .addValue(
                        "explanation",
                        result.explanationText());

        return jdbc.update(sql, params);
    }

    public void saveAll(
            List<AssessmentIndicatorResult> results) {

        if (results == null || results.size() != 3) {
            throw new IllegalArgumentException(
                "Exactly three indicator results are required"
            );
        }

        for (AssessmentIndicatorResult result : results) {

            int inserted = save(result);

            if (inserted != 1) {
                throw new IllegalStateException(
                    "Failed to save indicator result: "
                    + result.indicatorCode()
                );
            }
        }
    }

    private BigDecimal roundForStorage(BigDecimal value) {

        if (value == null) {
            return null;
        }

        return value.setScale(
                4,
                RoundingMode.HALF_UP
        );
    }
}
