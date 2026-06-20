package com.fundtracker.repository;

import com.fundtracker.model.entity.DcaPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DcaPlanRepository extends JpaRepository<DcaPlan, String> {
    List<DcaPlan> findByHoldingIdOrderByCreatedAtDesc(String holdingId);

    List<DcaPlan> findByStatus(String status);

    List<DcaPlan> findByStatusAndNextExecutionDateLessThanEqual(String status, LocalDate date);

    int deleteByHoldingId(String holdingId);
}
