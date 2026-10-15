package com.msme.repository;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.msme.entity.AssessmentIndicatorResult;
@Repository
public interface AssessmentIndicatorResultRepository extends JpaRepository<AssessmentIndicatorResult, Long> {

    List<AssessmentIndicatorResult> findByAssessmentIdOrderByIndicatorCode(Long assessmentId);

}
