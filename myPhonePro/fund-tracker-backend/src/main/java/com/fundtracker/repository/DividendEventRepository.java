package com.fundtracker.repository;

import com.fundtracker.model.entity.DividendEvent;
import com.fundtracker.model.enums.EventStatus;
import com.fundtracker.model.enums.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface DividendEventRepository extends JpaRepository<DividendEvent, String> {
    List<DividendEvent> findByHoldingIdOrderByDateDesc(String holdingId);
    List<DividendEvent> findByDateOrderByHoldingName(LocalDate date);
    List<DividendEvent> findByDateBetweenOrderByDate(LocalDate start, LocalDate end);
    List<DividendEvent> findByHoldingIdAndDateBetween(String holdingId, LocalDate start, LocalDate end);
    List<DividendEvent> findByTypeAndStatus(String type, String status);
    List<DividendEvent> findByHoldingIdAndStatus(String holdingId, EventStatus status);
    boolean existsByHoldingIdAndTypeAndDate(String holdingId, EventType type, LocalDate date);
    List<DividendEvent> findByHoldingIdAndTypeAndDate(String holdingId, EventType type, LocalDate date);
    int deleteByHoldingId(String holdingId);
}
