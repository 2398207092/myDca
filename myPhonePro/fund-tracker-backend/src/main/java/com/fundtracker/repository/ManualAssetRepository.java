package com.fundtracker.repository;

import com.fundtracker.model.entity.ManualAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ManualAssetRepository extends JpaRepository<ManualAsset, String> {
    List<ManualAsset> findAllByOrderByTypeAscAmountDesc();
    List<ManualAsset> findByType(String type);
    List<ManualAsset> findByUserId(String userId);
    List<ManualAsset> findByUserIdOrderByCreatedAtDesc(String userId);
    List<ManualAsset> findByUserIdAndType(String userId, String type);

    /** 定投扣款：找某账户下的资产（现金 / 加密币余额） */
    List<ManualAsset> findByUserIdAndAccountId(String userId, String accountId);
}
