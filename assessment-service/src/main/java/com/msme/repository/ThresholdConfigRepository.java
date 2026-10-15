
package com.msme.repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.msme.entity.ThresholdConfig;
import com.msme.enums.ThresholdStatus;

@Repository
public class ThresholdConfigRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ThresholdConfigRepository(
            NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<ThresholdConfig> rowMapper =
            (rs, rowNum) -> new ThresholdConfig(
                rs.getLong("THRESHOLD_CONFIG_ID"),
                rs.getLong("MSME_ID"),
                rs.getInt("VERSION_NO"),
                rs.getBigDecimal("REVENUE_DECLINE_PCT"),
                rs.getBigDecimal("OVERDUE_RECEIVABLE_PCT"),
                rs.getBigDecimal("CREDIT_UTILISATION_PCT"),
                ThresholdStatus.valueOf(
                    rs.getString("STATUS")
                ),
                rs.getLong("PROPOSED_BY_USER_ID"),
                rs.getTimestamp("PROPOSED_AT").toLocalDateTime(),
                rs.getObject("REVIEWED_BY_USER_ID", Long.class),
                rs.getTimestamp("REVIEWED_AT") == null
                    ? null
                    : rs.getTimestamp("REVIEWED_AT")
                        .toLocalDateTime(),
                rs.getString("REVIEW_REASON"),
                rs.getTimestamp("EFFECTIVE_FROM") == null
                    ? null
                    : rs.getTimestamp("EFFECTIVE_FROM")
                        .toLocalDateTime()
            );

    public Optional<ThresholdConfig> findById(Long id) {

        String sql = """
            SELECT *
            FROM THRESHOLD_CONFIG
            WHERE THRESHOLD_CONFIG_ID = :id
            """;

        List<ThresholdConfig> results = jdbc.query(
                sql,
                new MapSqlParameterSource("id", id),
                rowMapper
        );

        return results.stream().findFirst();
    }

    public List<ThresholdConfig> findByMsmeId(
            Long msmeId,
            int page,
            int size) {

        String sql = """
            SELECT *
            FROM THRESHOLD_CONFIG
            WHERE MSME_ID = :msmeId
            ORDER BY VERSION_NO DESC
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
            FROM THRESHOLD_CONFIG
            WHERE MSME_ID = :msmeId
            """;

        Long count = jdbc.queryForObject(
                sql,
                new MapSqlParameterSource("msmeId", msmeId),
                Long.class
        );

        return count == null ? 0 : count;
    }

    public int findNextVersion(Long msmeId) {

        String sql = """
            SELECT NVL(MAX(VERSION_NO), 0) + 1
            FROM THRESHOLD_CONFIG
            WHERE MSME_ID = :msmeId
            """;

        Integer version = jdbc.queryForObject(
                sql,
                new MapSqlParameterSource("msmeId", msmeId),
                Integer.class
        );

        return version == null ? 1 : version;
    }

    public Long save(ThresholdConfig threshold) {

        String sql = """
            INSERT INTO THRESHOLD_CONFIG (
                MSME_ID,
                VERSION_NO,
                REVENUE_DECLINE_PCT,
                OVERDUE_RECEIVABLE_PCT,
                CREDIT_UTILISATION_PCT,
                STATUS,
                PROPOSED_BY_USER_ID,
                EFFECTIVE_FROM
            )
            VALUES (
                :msmeId,
                :versionNo,
                :revenue,
                :overdue,
                :credit,
                'PENDING',
                :proposedBy,
                :effectiveFrom
            )
            """;

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                    .addValue("msmeId", threshold.msmeId())
                    .addValue("versionNo", threshold.versionNo())
                    .addValue("revenue",
                            threshold.revenueDeclinePct())
                    .addValue("overdue",
                            threshold.overdueReceivablePct())
                    .addValue("credit",
                            threshold.creditUtilisationPct())
                    .addValue("proposedBy",
                            threshold.proposedByUserId())
                    .addValue(
                            "effectiveFrom",
                            threshold.effectiveFrom() == null
                                ? null
                                : Timestamp.valueOf(
                                    threshold.effectiveFrom()),
                            Types.TIMESTAMP
                    );

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbc.update(
                sql,
                params,
                keyHolder,
                new String[]{"THRESHOLD_CONFIG_ID"}
        );

        Number key = keyHolder.getKey();

        if (key == null) {
            throw new IllegalStateException(
                    "Threshold ID was not generated");
        }

        return key.longValue();
    }

    public int review(
            Long thresholdId,
            ThresholdStatus decision,
            Long reviewerId,
            String reason) {

        String sql = """
            UPDATE THRESHOLD_CONFIG
            SET STATUS = :status,
                REVIEWED_BY_USER_ID = :reviewerId,
                REVIEWED_AT = SYSTIMESTAMP,
                REVIEW_REASON = :reason
            WHERE THRESHOLD_CONFIG_ID = :id
              AND STATUS = 'PENDING'
            """;

        return jdbc.update(
                sql,
                new MapSqlParameterSource()
                    .addValue("status", decision.name())
                    .addValue("reviewerId", reviewerId)
                    .addValue("reason", reason)
                    .addValue("id", thresholdId)
        );
    }

    public Optional<ThresholdConfig> findLatestApprovedEffective(
            Long msmeId,
            LocalDateTime asOf) {

        String sql = """
            SELECT *
            FROM THRESHOLD_CONFIG
            WHERE MSME_ID = :msmeId
              AND STATUS = 'APPROVED'
              AND EFFECTIVE_FROM <= :asOf
            ORDER BY EFFECTIVE_FROM DESC,
                     VERSION_NO DESC
            FETCH FIRST 1 ROW ONLY
            """;

        List<ThresholdConfig> results = jdbc.query(
                sql,
                new MapSqlParameterSource()
                    .addValue("msmeId", msmeId)
                    .addValue("asOf", Timestamp.valueOf(asOf)),
                rowMapper
        );

        return results.stream().findFirst();
    }
}
