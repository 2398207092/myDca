package com.fundtracker.repository;

import com.fundtracker.model.entity.BudgetRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BudgetRecordRepository extends JpaRepository<BudgetRecord, String> {

    List<BudgetRecord> findByUserIdAndCategoryIdInAndDeletedFalseOrderByOccurDateDescIdDesc(
            String userId, Collection<String> categoryIds);

    long countByCategoryIdAndDeletedFalse(String categoryId);

    Optional<BudgetRecord> findByIdAndUserIdAndDeletedFalse(String id, String userId);
}
