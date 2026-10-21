package com.msme.repository;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.msme.entity.ActivityLog;
@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    boolean existsByRelatedEntityTypeAndRelatedEntityIdAndActionCode(
            String relatedEntityType, Long relatedEntityId, String actionCode);

    Page<ActivityLog> findByRelatedEntityTypeAndRelatedEntityId(String relatedEntityType, Long relatedEntityId, Pageable page);

}
