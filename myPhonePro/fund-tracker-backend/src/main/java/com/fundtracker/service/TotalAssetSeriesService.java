package com.fundtracker.service;

import com.fundtracker.model.dto.TotalAssetSeriesDTO;
import com.fundtracker.model.entity.AssetSnapshot;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.entity.HoldingSnapshot;
import com.fundtracker.repository.AssetSnapshotRepository;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.repository.HoldingSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 总资产走势服务。
 * 职责：按快照日期分组聚合，叠加 asset_snapshots 的现金/BTC，生成总资产走势数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TotalAssetSeriesService {

    private final HoldingRepository holdingRepository;
    private final HoldingSnapshotRepository holdingSnapshotRepository;
    private final AssetSnapshotRepository assetSnapshotRepository;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * 总资产走势：按快照日期分组聚合，叠加 asset_snapshots 的现金/BTC。
     */
    public TotalAssetSeriesDTO getTotalAssetSeries(String range, String userId) {
        LocalDate startDate = resolveStartDate(range);

        // 获取当前用户的所有持仓 ID，用于过滤快照
        List<Holding> userHoldings = holdingRepository.findByUserIdAndDeletedFalseOrderByMarketValueDesc(userId);
        Set<String> userHoldingIds = userHoldings.stream()
                .map(Holding::getId)
                .collect(Collectors.toSet());
        // holdingId -> assetCategory 映射（用于按类别聚合市值折线）
        Map<String, String> holdingCategoryMap = userHoldings.stream()
                .collect(Collectors.toMap(Holding::getId, h -> h.getAssetCategory() == null ? "" : h.getAssetCategory()));

        List<HoldingSnapshot> allSnapshots = holdingSnapshotRepository
                .findBySnapshotDateAfterOrderBySnapshotDateAsc(startDate.minusDays(1));
        // 只保留属于当前用户的快照
        List<HoldingSnapshot> snapshots = allSnapshots.stream()
                .filter(s -> userHoldingIds.contains(s.getHoldingId()))
                .collect(Collectors.toList());
        if (snapshots.isEmpty()) {
            return TotalAssetSeriesDTO.builder()
                    .series(Collections.emptyList())
                    .totalChange(BigDecimal.ZERO)
                    .totalChangePercent(BigDecimal.ZERO)
                    .build();
        }

        // 按日期分组聚合持仓数据
        Map<LocalDate, List<HoldingSnapshot>> grouped = snapshots.stream()
                .collect(Collectors.groupingBy(HoldingSnapshot::getSnapshotDate, LinkedHashMap::new, Collectors.toList()));

        // 预加载 asset_snapshots（分类级，含现金/BTC），按 userId 过滤
        List<AssetSnapshot> assetSnapshots = assetSnapshotRepository.findByUserIdAndDateAfterOrderByDateAsc(userId, startDate.minusDays(1));
        Map<LocalDate, AssetSnapshot> assetMap = assetSnapshots.stream()
                .collect(Collectors.toMap(AssetSnapshot::getDate, a -> a, (a, b) -> a));

        List<TotalAssetSeriesDTO.TotalAssetPoint> points = new ArrayList<>();
        AssetSnapshot lastAsset = null;
        for (Map.Entry<LocalDate, List<HoldingSnapshot>> entry : grouped.entrySet()) {
            LocalDate date = entry.getKey();
            List<HoldingSnapshot> daySnapshots = entry.getValue();

            BigDecimal totalMarketValue = daySnapshots.stream()
                    .map(HoldingSnapshot::getMarketValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalShares = daySnapshots.stream()
                    .map(HoldingSnapshot::getShares)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalCostBasis = daySnapshots.stream()
                    .map(HoldingSnapshot::getCostBasis)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalProfitLoss = totalMarketValue.subtract(totalCostBasis);
            BigDecimal totalProfitLossPct = totalCostBasis.compareTo(BigDecimal.ZERO) > 0
                    ? totalProfitLoss.multiply(HUNDRED).divide(totalCostBasis, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            // 按类别聚合持仓市值（未分类归入 uncategorized）
            Map<String, BigDecimal> categoryValues = new LinkedHashMap<>();
            for (HoldingSnapshot s : daySnapshots) {
                String cat = holdingCategoryMap.getOrDefault(s.getHoldingId(), "");
                if (cat.isEmpty()) cat = "uncategorized";
                categoryValues.merge(cat, s.getMarketValue(), BigDecimal::add);
            }

            // 叠加现金/BTC（从 asset_snapshots 取，缺数据则用最近一天填充）
            AssetSnapshot asset = assetMap.getOrDefault(date, lastAsset);
            if (asset != null) {
                BigDecimal cash = asset.getCashValue() == null ? BigDecimal.ZERO : asset.getCashValue();
                BigDecimal crypto = asset.getCryptoValue() == null ? BigDecimal.ZERO : asset.getCryptoValue();
                totalMarketValue = totalMarketValue.add(cash).add(crypto);
                if (cash.compareTo(BigDecimal.ZERO) > 0) categoryValues.put("cash", cash);
                if (crypto.compareTo(BigDecimal.ZERO) > 0) categoryValues.put("crypto", crypto);
                lastAsset = asset;
            }

            points.add(TotalAssetSeriesDTO.TotalAssetPoint.builder()
                    .date(date)
                    .totalMarketValue(totalMarketValue)
                    .totalShares(totalShares)
                    .totalCostBasis(totalCostBasis)
                    .totalProfitLoss(totalProfitLoss)
                    .totalProfitLossPct(totalProfitLossPct)
                    .categoryValues(categoryValues)
                    .build());
        }

        // 区间变动 = 最新市值 - 最早市值
        BigDecimal firstMv = points.get(0).getTotalMarketValue();
        BigDecimal lastMv = points.get(points.size() - 1).getTotalMarketValue();
        BigDecimal totalChange = lastMv.subtract(firstMv);
        BigDecimal totalChangePercent = firstMv.compareTo(BigDecimal.ZERO) > 0
                ? totalChange.multiply(HUNDRED).divide(firstMv, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return TotalAssetSeriesDTO.builder()
                .series(points)
                .totalChange(totalChange)
                .totalChangePercent(totalChangePercent)
                .build();
    }

    private LocalDate resolveStartDate(String range) {
        if (range == null) range = "month";
        switch (range) {
            case "quarter":
                return LocalDate.now().minusDays(90);
            case "all":
                return LocalDate.of(1970, 1, 1);
            case "month":
            default:
                return LocalDate.now().minusDays(30);
        }
    }
}