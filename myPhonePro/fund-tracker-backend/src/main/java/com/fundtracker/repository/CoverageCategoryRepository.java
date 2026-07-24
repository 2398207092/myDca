package com.fundtracker.repository;

import com.fundtracker.model.entity.CoverageCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CoverageCategoryRepository extends JpaRepository<CoverageCategory, String> {
    List<CoverageCategory> findByUserIdOrderByNameAsc(String userId);
    List<CoverageCategory> findByUserId(String userId);
}
