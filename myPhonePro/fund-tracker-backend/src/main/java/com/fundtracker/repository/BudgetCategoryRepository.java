package com.fundtracker.repository;

import com.fundtracker.model.entity.BudgetCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BudgetCategoryRepository extends JpaRepository<BudgetCategory, String> {

    List<BudgetCategory> findByUserIdAndYearAndDeletedFalseOrderBySortOrderAscIdAsc(String userId, Integer year);

    Optional<BudgetCategory> findByIdAndUserIdAndDeletedFalse(String id, String userId);

    long countByUserIdAndYearAndDeletedFalse(String userId, Integer year);
}
