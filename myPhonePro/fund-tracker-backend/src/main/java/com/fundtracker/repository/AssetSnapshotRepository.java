package com.fundtracker.repository;

import com.fundtracker.model.entity.AssetSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AssetSnapshotRepository extends JpaRepository<AssetSnapshot, String> {
    Optional<AssetSnapshot> findTopByOrderByDateDesc();
    Optional<AssetSnapshot> findByDate(LocalDate date);
    List<AssetSnapshot> findByDateAfterOrderByDateAsc(LocalDate date);

    List<AssetSnapshot> findByUserIdOrderByDateAsc(String userId);
    List<AssetSnapshot> findByUserIdAndDateAfterOrderByDateAsc(String userId, LocalDate date);
    List<AssetSnapshot> findByUserIdAndDate(String userId, LocalDate date);
    Optional<AssetSnapshot> findTopByUserIdOrderByDateDesc(String userId);

    // 批量删除某用户某日的快照（覆盖模式），flushAutomatically=true 确保 DELETE 先于后续 INSERT 执行
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM AssetSnapshot a WHERE a.userId = :userId AND a.date = :date")
    int deleteByUserIdAndDate(@Param("userId") String userId, @Param("date") LocalDate date);
}
