package com.fundtracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundtracker.model.dto.AssetHistoryDTO;
import com.fundtracker.model.dto.AssetOverviewDTO;
import com.fundtracker.model.entity.AssetSnapshot;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetOverviewService {

    private final HoldingRepository holdingRepository;
    private final ManualAssetService manualAssetService;
    private final AssetSnapshotRepository assetSnapshotRepository;
    private final ObjectMapper objectMapper;

    // 颜色映射
    private static final Map<String, String> CATEGORY_COLORS = Map.of(
            "cash", "#34A853",       // 绿色 - 现金
            "crypto", "#F59E0B",     // 橙色 - 比特币
            "us_stock", "#3B82F6",   // 蓝色 - 美股
            "gold", "#F59E0B",       // 金色 - 黄金
            "dividend", "#EAB308"    // 黄色 - 红利
    );

    private static final Map<String, String> CATEGORY_NAMES = Map.of(
            "cash", "现金",
            "crypto", "比特币",
            "us_stock", "美股",
            "gold", "黄金",
            "dividend", "红利"
    );

    public AssetOverviewDTO getOverview() {
        // 计算各类资产金额
        BigDecimal cashValue = manualAssetService.getTotalByType("cash");
        BigDecimal cryptoValue = manualAssetService.getTotalByType("crypto");
        BigDecimal usStockValue = getHoldingTotalByCategory("us_stock");
        BigDecimal goldValue = getHoldingTotalByCategory("gold");
        BigDecimal dividendValue = getHoldingTotalByCategory("dividend");

        BigDecimal totalValue = cashValue.add(cryptoValue).add(usStockValue)
                .add(goldValue).add(dividendValue);

        // 计算变化（对比最近快照）
        BigDecimal weeklyChange = BigDecimal.ZERO;
        BigDecimal weeklyChangePercent = BigDecimal.ZERO;
        BigDecimal monthlyChange = BigDecimal.ZERO;
        BigDecimal monthlyChangePercent = BigDecimal.ZERO;

        Optional<AssetSnapshot> latestSnapshot = assetSnapshotRepository.findTopByOrderByDateDesc();
        if (latestSnapshot.isPresent()) {
            BigDecimal prevTotal = latestSnapshot.get().getTotalValue();
            if (prevTotal.compareTo(BigDecimal.ZERO) > 0) {
                weeklyChange = totalValue.subtract(prevTotal);
                weeklyChangePercent = weeklyChange.multiply(BigDecimal.valueOf(100))
                        .divide(prevTotal, 2, RoundingMode.HALF_UP);

                // 查 7 天前的快照
                LocalDate weekAgo = LocalDate.now().minusDays(7);
                Optional<AssetSnapshot> weekAgoSnapshot = assetSnapshotRepository.findByDate(weekAgo);
                if (weekAgoSnapshot.isPresent()) {
                    BigDecimal weekAgoTotal = weekAgoSnapshot.get().getTotalValue();
                    monthlyChange = totalValue.subtract(weekAgoTotal);
                    if (weekAgoTotal.compareTo(BigDecimal.ZERO) > 0) {
                        monthlyChangePercent = monthlyChange.multiply(BigDecimal.valueOf(100))
                                .divide(weekAgoTotal, 2, RoundingMode.HALF_UP);
                    }
                }
            }
        }

        // 构建分类明细
        List<AssetOverviewDTO.CategoryDetail> categories = buildCategoryDetails(
                cashValue, cryptoValue, usStockValue, goldValue, dividendValue, totalValue);

        return AssetOverviewDTO.builder()
                .totalValue(totalValue)
                .cashValue(cashValue)
                .cryptoValue(cryptoValue)
                .usStockValue(usStockValue)
                .goldValue(goldValue)
                .dividendValue(dividendValue)
                .weeklyChange(weeklyChange)
                .weeklyChangePercent(weeklyChangePercent)
                .monthlyChange(monthlyChange)
                .monthlyChangePercent(monthlyChangePercent)
                .categories(categories)
                .build();
    }

    public AssetHistoryDTO getHistory(String range) {
        LocalDate startDate;
        if ("month".equals(range)) {
            startDate = LocalDate.now().minusDays(30);
        } else {
            startDate = LocalDate.now().minusDays(7); // 默认 week
        }

        List<AssetSnapshot> snapshots = assetSnapshotRepository.findByDateAfterOrderByDateAsc(startDate);

        List<AssetHistoryDTO.Point> series = snapshots.stream()
                .map(s -> AssetHistoryDTO.Point.builder()
                        .date(s.getDate())
                        .value(s.getTotalValue())
                        .build())
                .collect(Collectors.toList());

        // 计算变化
        BigDecimal totalChange = BigDecimal.ZERO;
        BigDecimal totalChangePercent = BigDecimal.ZERO;
        if (snapshots.size() >= 2) {
            BigDecimal first = snapshots.get(0).getTotalValue();
            BigDecimal last = snapshots.get(snapshots.size() - 1).getTotalValue();
            totalChange = last.subtract(first);
            if (first.compareTo(BigDecimal.ZERO) > 0) {
                totalChangePercent = totalChange.multiply(BigDecimal.valueOf(100))
                        .divide(first, 2, RoundingMode.HALF_UP);
            }
        }

        return AssetHistoryDTO.builder()
                .series(series)
                .totalChange(totalChange)
                .totalChangePercent(totalChangePercent)
                .newInvestment(BigDecimal.ZERO) // 可后续从交易数据计算
                .dividendIncome(BigDecimal.ZERO) // 可后续从分红数据计算
                .build();
    }

    @Transactional
    public void snapshotToday() {
        LocalDate today = LocalDate.now();
        // 避免重复快照
        if (assetSnapshotRepository.findByDate(today).isPresent()) {
            log.info("今日快照已存在，跳过");
            return;
        }

        AssetOverviewDTO overview = getOverview();

        AssetSnapshot snapshot = AssetSnapshot.builder()
                .date(today)
                .totalValue(overview.getTotalValue())
                .cashValue(overview.getCashValue())
                .cryptoValue(overview.getCryptoValue())
                .usStockValue(overview.getUsStockValue())
                .goldValue(overview.getGoldValue())
                .dividendValue(overview.getDividendValue())
                .build();

        // 尝试序列化 breakdownJson
        try {
            snapshot.setBreakdownJson(objectMapper.writeValueAsString(overview.getCategories()));
        } catch (JsonProcessingException e) {
            log.warn("序列化 breakdownJson 失败", e);
        }

        assetSnapshotRepository.save(snapshot);
        log.info("已生成今日资产快照: ¥{}", overview.getTotalValue());
    }

    private BigDecimal getHoldingTotalByCategory(String category) {
        List<Holding> holdings = holdingRepository.findByAssetCategoryAndDeletedFalse(category);
        return holdings.stream()
                .map(h -> h.getMarketValue() != null ? h.getMarketValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<AssetOverviewDTO.CategoryDetail> buildCategoryDetails(
            BigDecimal cashValue, BigDecimal cryptoValue, BigDecimal usStockValue,
            BigDecimal goldValue, BigDecimal dividendValue, BigDecimal totalValue) {

        List<AssetOverviewDTO.CategoryDetail> details = new ArrayList<>();

        details.add(buildCategory("cash", "现金", cashValue, totalValue));
        details.add(buildCategory("crypto", "比特币", cryptoValue, totalValue));
        details.add(buildCategory("us_stock", "美股", usStockValue, totalValue));
        details.add(buildCategory("gold", "黄金", goldValue, totalValue));
        details.add(buildCategory("dividend", "红利", dividendValue, totalValue));

        return details.stream()
                .filter(d -> d.getValue().compareTo(BigDecimal.ZERO) > 0)
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .collect(Collectors.toList());
    }

    private AssetOverviewDTO.CategoryDetail buildCategory(String type, String name,
                                                           BigDecimal value, BigDecimal totalValue) {
        double percentage = 0;
        if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
            percentage = value.multiply(BigDecimal.valueOf(100))
                    .divide(totalValue, 2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        List<AssetOverviewDTO.HoldingItem> items = new ArrayList<>();

        // 对于基金类资产，列出具体持仓
        if ("us_stock".equals(type) || "gold".equals(type) || "dividend".equals(type)) {
            List<Holding> holdings = holdingRepository.findByAssetCategoryAndDeletedFalse(type);
            for (Holding h : holdings) {
                items.add(AssetOverviewDTO.HoldingItem.builder()
                        .id(h.getId())
                        .name(h.getName())
                        .value(h.getMarketValue() != null ? h.getMarketValue() : BigDecimal.ZERO)
                        .build());
            }
        }

        // 对于手动资产，列出明细
        if ("cash".equals(type) || "crypto".equals(type)) {
            var manualAssets = manualAssetService.listManualAssets().stream()
                    .filter(a -> type.equals(a.getType()))
                    .collect(Collectors.toList());
            for (var a : manualAssets) {
                items.add(AssetOverviewDTO.HoldingItem.builder()
                        .id(a.getId())
                        .name(a.getName())
                        .value(a.getAmount())
                        .build());
            }
        }

        return AssetOverviewDTO.CategoryDetail.builder()
                .name(name)
                .type(type)
                .value(value)
                .percentage(percentage)
                .color(CATEGORY_COLORS.getOrDefault(type, "#999999"))
                .items(items)
                .build();
    }
}
