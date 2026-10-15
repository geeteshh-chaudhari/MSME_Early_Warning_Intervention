package com.msme.repository;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.msme.entity.FollowUpReview;
@Repository
public interface FollowUpReviewRepository extends JpaRepository<FollowUpReview, Long> {

    boolean existsByInterventionIdAndAssessmentId(Long interventionId, Long assessmentId);
    Page<FollowUpReview> findByInterventionId(Long interventionId, Pageable page);
    List<FollowUpReview> findByInterventionId(Long interventionId);

}
