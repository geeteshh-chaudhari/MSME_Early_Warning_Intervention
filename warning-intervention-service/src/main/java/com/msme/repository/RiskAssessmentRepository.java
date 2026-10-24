package com.msme.repository;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.msme.entity.RiskAssessment;
@Repository
public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from RiskAssessment a where a.assessmentId = :id")
    Optional<RiskAssessment> findLockedById(@Param("id") Long id);

}
