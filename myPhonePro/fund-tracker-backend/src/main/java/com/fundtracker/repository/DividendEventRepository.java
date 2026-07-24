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
    List<DividendEvent> findByStatus(EventStatus status);
    List<DividendEvent> findByDateBeforeAndStatus(LocalDate date, EventStatus status);
    boolean existsByHoldingIdAndTypeAndDate(String holdingId, EventType type, LocalDate date);
    List<DividendEvent> findByHoldingIdAndTypeAndDate(String holdingId, EventType type, LocalDate date);
    int deleteByHoldingIdAndTypeAndDate(String holdingId, EventType type, LocalDate date);
    int deleteByHoldingId(String holdingId);

    // UserId-filtered queries
    List<DividendEvent> findByUserIdOrderByDateDesc(String userId);
    List<DividendEvent> findByHoldingIdAndUserIdOrderByDateDesc(String holdingId, String userId);
    List<DividendEvent> findByUserIdAndDateBetweenOrderByDate(String userId, LocalDate start, LocalDate end);
    List<DividendEvent> findByDateBetweenAndUserIdOrderByDate(LocalDate start, LocalDate end, String userId);
    List<DividendEvent> findByHoldingIdAndDateBetweenAndUserId(String holdingId, LocalDate start, LocalDate end, String userId);
    List<DividendEvent> findByTypeAndStatusAndUserId(String type, String status, String userId);
    List<DividendEvent> findByHoldingIdAndStatusAndUserId(String holdingId, EventStatus status, String userId);
    List<DividendEvent> findByStatusAndUserId(EventStatus status, String userId);
    List<DividendEvent> findByDateAndUserIdOrderByHoldingName(LocalDate date, String userId);
}
