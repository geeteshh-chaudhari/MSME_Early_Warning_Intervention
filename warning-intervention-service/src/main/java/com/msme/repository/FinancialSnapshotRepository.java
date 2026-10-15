package com.msme.repository;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.msme.entity.FinancialSnapshot;
@Repository
public interface FinancialSnapshotRepository extends JpaRepository<FinancialSnapshot, Long> {

}
