package com.fundtracker.repository;

import com.fundtracker.model.entity.CoverageCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoverageCategoryRepository extends JpaRepository<CoverageCategory, String> {
}
