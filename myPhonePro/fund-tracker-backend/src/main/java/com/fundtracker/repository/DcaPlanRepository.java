package com.fundtracker.repository;

import com.fundtracker.model.entity.DcaPlan;
import com.fundtracker.model.enums.DcaPlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DcaPlanRepository extends JpaRepository<DcaPlan, String> {
    List<DcaPlan> findByHoldingIdOrderByCreatedAtDesc(String holdingId);
    List<DcaPlan> findByUserId(String userId);
    List<DcaPlan> findByHoldingIdAndUserIdOrderByCreatedAtDesc(String holdingId, String userId);

    List<DcaPlan> findByStatus(DcaPlanStatus status);

    List<DcaPlan> findByStatusAndNextExecutionDateLessThanEqual(DcaPlanStatus status, LocalDate date);

    int deleteByHoldingId(String holdingId);
    Optional<DcaPlan> findByIdAndUserId(String id, String userId);
}
