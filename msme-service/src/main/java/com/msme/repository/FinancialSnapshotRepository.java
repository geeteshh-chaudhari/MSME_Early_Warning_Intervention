package com.msme.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.msme.entity.FinancialSnapshot;

@Repository
public interface FinancialSnapshotRepository
        extends JpaRepository<FinancialSnapshot, Long> {

    /*
     * Check whether an MSME already has a snapshot
     * for a particular reporting month.
     *
     * Supports:
     * - duplicate prevention
     * - assessment readiness checking
     */
    boolean existsByMsmeIdAndReportingPeriod(
            Long msmeId,
            LocalDate reportingPeriod
    );

    /*
     * Financial history of one MSME,
     * ordered by reporting month.
     *
     * Important:
     * We order by REPORTING_PERIOD, not SUBMITTED_AT,
     * because historical months may be submitted later.
     */
    List<FinancialSnapshot>
            findByMsmeIdOrderByReportingPeriodAsc(
                    Long msmeId
            );

    /*
     * Find one MSME's snapshot for one reporting month.
     */
    Optional<FinancialSnapshot>
            findByMsmeIdAndReportingPeriod(
                    Long msmeId,
                    LocalDate reportingPeriod
            );

    /*
     * Risk Admin:
     * View all financial snapshots ordered by
     * reporting period.
     */
    List<FinancialSnapshot>
            findAllByOrderByReportingPeriodAsc();
}