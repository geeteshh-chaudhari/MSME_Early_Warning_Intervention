package com.msme.repository;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.msme.entity.Warning;
@Repository
public interface WarningRepository extends JpaRepository<Warning, Long> {

    Optional<Warning> findByAssessmentId(Long assessmentId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Warning w where w.warningId = :id")
    Optional<Warning> findLockedById(@Param("id") Long id);
    @Query("select w from Warning w, RiskAssessment a where w.assessmentId = a.assessmentId and a.msmeId = :msmeId and (:status is null or w.warningStatus = :status)")
    Page<Warning> findForMsme(@Param("msmeId") Long msmeId, @Param("status") String status, Pageable page);

}
