package com.fundtracker.repository;

import com.fundtracker.model.entity.LiveExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LiveExpenseRepository extends JpaRepository<LiveExpense, String> {
    List<LiveExpense> findByDeletedFalseOrderBySortOrderAsc();
    long countByDeletedFalse();
}
