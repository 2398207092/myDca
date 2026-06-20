package com.fundtracker.repository;

import com.fundtracker.model.entity.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, String> {
    List<Holding> findByTypeAndDeletedFalse(String type);
    List<Holding> findByDeletedFalseOrderByMarketValueDesc();
    List<Holding> findByAssetCategoryAndDeletedFalse(String assetCategory);
    Optional<Holding> findByIdAndDeletedFalse(String id);
    boolean existsByCodeAndDeletedFalse(String code);
    List<Holding> findByNameContainingOrCodeContainingAndDeletedFalse(String name, String code);
    List<Holding> findByCodeAndDeletedFalse(String code);
    @Query("SELECT DISTINCT h.code FROM Holding h WHERE h.deleted = false AND h.code IS NOT NULL")
    List<String> findDistinctCodesByDeletedFalse();
}
