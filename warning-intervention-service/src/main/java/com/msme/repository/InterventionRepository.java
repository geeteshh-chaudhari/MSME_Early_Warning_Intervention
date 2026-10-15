package com.msme.repository;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.msme.entity.Intervention;
@Repository
public interface InterventionRepository extends JpaRepository<Intervention, Long> {
    @Query("select i.warningId from Intervention i where i.interventionId = :id")
    Optional<Long> findWarningId(@Param("id") Long id);

    List<Intervention> findByWarningIdAndIsActive(Long warningId, String isActive);
    Page<Intervention> findByWarningId(Long warningId, Pageable page);

}
