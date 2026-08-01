package com.fundtracker.repository;

import com.fundtracker.model.entity.HoldingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HoldingSnapshotRepository extends JpaRepository<HoldingSnapshot, String> {

    // 查某持仓的所有快照（按日期升序）
    List<HoldingSnapshot> findByHoldingIdOrderBySnapshotDateAsc(String holdingId);

    // 查某持仓在某日期之后的所有快照
    List<HoldingSnapshot> findByHoldingIdAndSnapshotDateAfterOrderBySnapshotDateAsc(String holdingId, LocalDate date);

    // 查某持仓的最新两条快照（用于计算 vs 上期变化）
    List<HoldingSnapshot> findTop2ByHoldingIdOrderBySnapshotDateDesc(String holdingId);

    // 查某日期的所有快照
    List<HoldingSnapshot> findBySnapshotDate(LocalDate date);

    // 删除某日期的所有快照（覆盖模式用），flushAutomatically=true 确保 DELETE 先于后续 INSERT 执行
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("DELETE FROM HoldingSnapshot h WHERE h.snapshotDate = :date")
    int deleteBySnapshotDate(@org.springframework.data.repository.query.Param("date") LocalDate date);

    // 删除某用户某日期的快照（按 holdingId 关联过滤），避免误删其他用户数据
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("DELETE FROM HoldingSnapshot hs WHERE hs.snapshotDate = :date AND hs.holdingId IN " +
           "(SELECT h.id FROM Holding h WHERE h.userId = :userId)")
    int deleteBySnapshotDateAndUserId(@org.springframework.data.repository.query.Param("date") LocalDate date,
                                      @org.springframework.data.repository.query.Param("userId") String userId);

    // 查某日期之后的所有快照（用于总资产走势）
    List<HoldingSnapshot> findBySnapshotDateAfterOrderBySnapshotDateAsc(LocalDate date);

    // 判断某持仓在某日期是否已有快照
    Optional<HoldingSnapshot> findByHoldingIdAndSnapshotDate(String holdingId, LocalDate date);

    // 查所有持仓的最新快照
    @Query("SELECT h FROM HoldingSnapshot h WHERE h.snapshotDate = " +
           "(SELECT MAX(h2.snapshotDate) FROM HoldingSnapshot h2 WHERE h2.holdingId = h.holdingId)")
    List<HoldingSnapshot> findLatestSnapshotsForAllHoldings();
}
