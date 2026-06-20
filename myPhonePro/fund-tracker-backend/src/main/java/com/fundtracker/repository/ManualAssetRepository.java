package com.fundtracker.repository;

import com.fundtracker.model.entity.ManualAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ManualAssetRepository extends JpaRepository<ManualAsset, String> {
    List<ManualAsset> findAllByOrderByTypeAscAmountDesc();
    List<ManualAsset> findByType(String type);
}
