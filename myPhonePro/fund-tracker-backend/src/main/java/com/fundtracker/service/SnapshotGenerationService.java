package com.fundtracker.service;

import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.entity.HoldingSnapshot;
import com.fundtracker.model.entity.ManualAsset;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.repository.HoldingSnapshotRepository;
import com.fundtracker.repository.ManualAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 生成持仓级快照。
 * 职责：为所有持仓或指定用户的持仓生成今日快照（覆盖模式）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotGenerationService {

    private final HoldingRepository holdingRepository;
    private final HoldingSnapshotRepository holdingSnapshotRepository;
    private final ManualAssetRepository manualAssetRepository;
    private final AssetOverviewService assetOverviewService;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * 为所有未删除持仓生成今日快照。若当天已有快照，先删除再重建。
     * 无参版本：定时任务使用，对所有用户分别执行快照。
     */
    @Transactional
    public void snapshotAllHoldings() {
        List<Holding> allHoldings = holdingRepository.findByDeletedFalseOrderByMarketValueDesc();
        Set<String> userIds = allHoldings.stream()
                .map(Holding::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (String uid : userIds) {
            snapshotAllHoldings(uid);
        }
    }

    /**
     * 为指定用户的所有未删除持仓生成今日快照。若当天已有快照，先删除再重建。
     */
    @Transactional
    public void snapshotAllHoldings(String userId) {
        List<Holding> holdings = holdingRepository.findByUserIdAndDeletedFalseOrderByMarketValueDesc(userId);
        if (holdings.isEmpty()) {
            log.info("快照任务：没有有效持仓，跳过");
            return;
        }
        LocalDate today = LocalDate.now();
        // 当前总资产 = Σ持仓市值 + 现金 + BTC
        BigDecimal totalValue = computeTotalValue(holdings, userId);

        // 先清理当天旧快照（覆盖模式，按用户过滤，避免误删其他用户数据）
        int deleted = holdingSnapshotRepository.deleteBySnapshotDateAndUserId(today, userId);
        if (deleted > 0) {
            log.info("已清理 {} 条今日快照（用户 {}）", deleted, userId);
        }

        int created = 0;
        for (Holding h : holdings) {
            holdingSnapshotRepository.save(buildSnapshot(h, today, totalValue));
            created++;
        }
        log.info("快照任务完成：共 {} 个持仓，新建快照 {} 条", holdings.size(), created);

        // 同步生成当天资产级快照（现金/BTC/分类总值），
        // 否则走势图"今天"的现金/BTC 会回退沿用上一天，导致金额不即时。
        assetOverviewService.snapshotToday(userId);
    }

    /**
     * P1-22：净值 22:00 刷新完成后，对当日已有快照但「快照市值/份额 ≠ 持仓当前值」的持仓
     * 重拍当日快照（先删后插，flush 保证 DELETE 先于 INSERT，满足 uk_holding_date 唯一约束）。
     * 白天生成的快照若用前日净值计价，此处按刷新后的最新市值覆盖修正。
     *
     * @return 重拍条数
     */
    @Transactional
    public int resnapshotStaleToday() {
        List<Holding> holdings = holdingRepository.findByDeletedFalseOrderByMarketValueDesc();
        if (holdings.isEmpty()) {
            return 0;
        }
        LocalDate today = LocalDate.now();
        Map<String, BigDecimal> totalValueCache = new HashMap<>();
        int resnapped = 0;
        for (Holding h : holdings) {
            Optional<HoldingSnapshot> existing =
                    holdingSnapshotRepository.findByHoldingIdAndSnapshotDate(h.getId(), today);
            if (existing.isEmpty() || !isSnapshotStale(existing.get(), h)) {
                continue;
            }
            holdingSnapshotRepository.delete(existing.get());
            holdingSnapshotRepository.flush();
            BigDecimal totalValue = totalValueCache.computeIfAbsent(h.getUserId(),
                    uid -> computeTotalValue(filterByUser(holdings, uid), uid));
            holdingSnapshotRepository.save(buildSnapshot(h, today, totalValue));
            resnapped++;
        }
        if (resnapped > 0) {
            log.info("快照重拍：{} 条当日快照因净值刷新后口径过期被覆盖重拍", resnapped);
        }
        return resnapped;
    }

    /** 当日无快照的持仓不生成新快照；市值差超过 1 分或份额差超过最小精度视为过期 */
    private boolean isSnapshotStale(HoldingSnapshot snapshot, Holding holding) {
        BigDecimal snapMv = snapshot.getMarketValue() == null ? BigDecimal.ZERO : snapshot.getMarketValue();
        BigDecimal curMv = holding.getMarketValue() == null ? BigDecimal.ZERO : holding.getMarketValue();
        if (snapMv.subtract(curMv).abs().compareTo(new BigDecimal("0.01")) > 0) {
            return true;
        }
        BigDecimal snapShares = snapshot.getShares() == null ? BigDecimal.ZERO : snapshot.getShares();
        BigDecimal curShares = holding.getShares() == null ? BigDecimal.ZERO : holding.getShares();
        return snapShares.subtract(curShares).abs().compareTo(new BigDecimal("0.00000001")) > 0;
    }

    private List<Holding> filterByUser(List<Holding> holdings, String userId) {
        return holdings.stream()
                .filter(h -> Objects.equals(h.getUserId(), userId))
                .collect(Collectors.toList());
    }

    private HoldingSnapshot buildSnapshot(Holding h, LocalDate snapshotDate, BigDecimal totalValue) {
        BigDecimal costBasis = h.getNetInvestment() == null ? BigDecimal.ZERO : h.getNetInvestment();
        BigDecimal marketValue = h.getMarketValue() == null ? BigDecimal.ZERO : h.getMarketValue();
        BigDecimal profitLoss = marketValue.subtract(costBasis);
        BigDecimal profitLossPct = costBasis.compareTo(BigDecimal.ZERO) > 0
                ? profitLoss.multiply(HUNDRED).divide(costBasis, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal pctOfTotal = totalValue.compareTo(BigDecimal.ZERO) > 0
                ? marketValue.multiply(HUNDRED).divide(totalValue, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return HoldingSnapshot.builder()
                .holdingId(h.getId())
                .snapshotDate(snapshotDate)
                .marketValue(marketValue)
                .shares(h.getShares())
                .costBasis(costBasis)
                .profitLoss(profitLoss)
                .profitLossPct(profitLossPct)
                .pctOfTotal(pctOfTotal)
                .build();
    }

    /** 计算当前总资产 = Σ持仓市值 + Σ现金 + Σ比特币 */
    private BigDecimal computeTotalValue(List<Holding> holdings, String userId) {
        BigDecimal holdingSum = holdings.stream()
                .map(Holding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal manualSum = manualAssetRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ManualAsset::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return holdingSum.add(manualSum);
    }
}