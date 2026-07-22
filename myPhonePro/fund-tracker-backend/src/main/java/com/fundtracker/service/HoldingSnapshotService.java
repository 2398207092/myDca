package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.AnnualizedReturnDTO;
import com.fundtracker.model.dto.HoldingDiffDTO;
import com.fundtracker.model.dto.HoldingSeriesDTO;
import com.fundtracker.model.dto.TotalAssetSeriesDTO;
import com.fundtracker.model.entity.AssetSnapshot;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.entity.HoldingSnapshot;
import com.fundtracker.model.entity.ManualAsset;
import com.fundtracker.model.entity.Transaction;
import com.fundtracker.model.enums.TransactionType;
import com.fundtracker.repository.AssetSnapshotRepository;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.repository.HoldingSnapshotRepository;
import com.fundtracker.repository.ManualAssetRepository;
import com.fundtracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 持仓级快照服务：生成快照、总资产走势、单持仓走势、vs上期变化、年化收益率(IRR)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingSnapshotService {

    private final HoldingRepository holdingRepository;
    private final HoldingSnapshotRepository holdingSnapshotRepository;
    private final TransactionRepository transactionRepository;
    private final ManualAssetRepository manualAssetRepository;
    private final AssetSnapshotRepository assetSnapshotRepository;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    /** 仅这三类持仓参与年化收益率计算 */
    private static final List<String> ANNUALIZED_CATEGORIES = List.of("us_stock", "gold", "dividend");

    // ======================================================================
    // 4.1 生成快照
    // ======================================================================

    /**
     * 为所有未删除持仓生成今日快照。幂等：同一天同持仓不重复生成。
     */
    @Transactional
    public void snapshotAllHoldings() {
        List<Holding> holdings = holdingRepository.findByDeletedFalseOrderByMarketValueDesc();
        if (holdings.isEmpty()) {
            log.info("快照任务：没有有效持仓，跳过");
            return;
        }
        LocalDate today = LocalDate.now();
        // 当前总资产 = Σ持仓市值 + 现金 + BTC
        BigDecimal totalValue = computeTotalValue(holdings);

        int created = 0;
        for (Holding h : holdings) {
            // 幂等检查
            if (holdingSnapshotRepository.findByHoldingIdAndSnapshotDate(h.getId(), today).isPresent()) {
                continue;
            }
            BigDecimal costBasis = h.getNetInvestment() == null ? BigDecimal.ZERO : h.getNetInvestment();
            BigDecimal marketValue = h.getMarketValue() == null ? BigDecimal.ZERO : h.getMarketValue();
            BigDecimal profitLoss = marketValue.subtract(costBasis);
            BigDecimal profitLossPct = costBasis.compareTo(BigDecimal.ZERO) > 0
                    ? profitLoss.multiply(HUNDRED).divide(costBasis, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal pctOfTotal = totalValue.compareTo(BigDecimal.ZERO) > 0
                    ? marketValue.multiply(HUNDRED).divide(totalValue, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            HoldingSnapshot snapshot = HoldingSnapshot.builder()
                    .holdingId(h.getId())
                    .snapshotDate(today)
                    .marketValue(marketValue)
                    .shares(h.getShares())
                    .costBasis(costBasis)
                    .profitLoss(profitLoss)
                    .profitLossPct(profitLossPct)
                    .pctOfTotal(pctOfTotal)
                    .build();
            holdingSnapshotRepository.save(snapshot);
            created++;
        }
        log.info("快照任务完成：共 {} 个持仓，新建快照 {} 条", holdings.size(), created);
    }

    /** 计算当前总资产 = Σ持仓市值 + Σ现金 + Σ比特币 */
    private BigDecimal computeTotalValue(List<Holding> holdings) {
        BigDecimal holdingSum = holdings.stream()
                .map(Holding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal manualSum = manualAssetRepository.findAll().stream()
                .map(ManualAsset::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return holdingSum.add(manualSum);
    }

    // ======================================================================
    // 4.2 总资产走势
    // ======================================================================

    /**
     * 总资产走势：按快照日期分组聚合，叠加 asset_snapshots 的现金/BTC。
     */
    public TotalAssetSeriesDTO getTotalAssetSeries(String range) {
        LocalDate startDate = resolveStartDate(range);
        List<HoldingSnapshot> snapshots = holdingSnapshotRepository
                .findBySnapshotDateAfterOrderBySnapshotDateAsc(startDate.minusDays(1));
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

        // 预加载 asset_snapshots（分类级，含现金/BTC）
        List<AssetSnapshot> assetSnapshots = assetSnapshotRepository.findByDateAfterOrderByDateAsc(startDate.minusDays(1));
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

            // 叠加现金/BTC（从 asset_snapshots 取，缺数据则用最近一天填充）
            AssetSnapshot asset = assetMap.getOrDefault(date, lastAsset);
            if (asset != null) {
                BigDecimal cash = asset.getCashValue() == null ? BigDecimal.ZERO : asset.getCashValue();
                BigDecimal crypto = asset.getCryptoValue() == null ? BigDecimal.ZERO : asset.getCryptoValue();
                totalMarketValue = totalMarketValue.add(cash).add(crypto);
                lastAsset = asset;
            }

            points.add(TotalAssetSeriesDTO.TotalAssetPoint.builder()
                    .date(date)
                    .totalMarketValue(totalMarketValue)
                    .totalShares(totalShares)
                    .totalCostBasis(totalCostBasis)
                    .totalProfitLoss(totalProfitLoss)
                    .totalProfitLossPct(totalProfitLossPct)
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

    // ======================================================================
    // 4.3 单持仓走势
    // ======================================================================

    public HoldingSeriesDTO getHoldingSeries(String holdingId, String range) {
        Holding holding = holdingRepository.findByIdAndDeletedFalse(holdingId)
                .orElseThrow(BusinessException::holdingNotFound);
        LocalDate startDate = resolveStartDate(range);
        List<HoldingSnapshot> snapshots = holdingSnapshotRepository
                .findByHoldingIdAndSnapshotDateAfterOrderBySnapshotDateAsc(holdingId, startDate.minusDays(1));

        List<HoldingSeriesDTO.HoldingPoint> points = snapshots.stream()
                .map(s -> HoldingSeriesDTO.HoldingPoint.builder()
                        .date(s.getSnapshotDate())
                        .marketValue(s.getMarketValue())
                        .shares(s.getShares())
                        .costBasis(s.getCostBasis())
                        .profitLoss(s.getProfitLoss())
                        .profitLossPct(s.getProfitLossPct())
                        .pctOfTotal(s.getPctOfTotal())
                        .build())
                .collect(Collectors.toList());

        return HoldingSeriesDTO.builder()
                .holding(HoldingSeriesDTO.HoldingInfo.builder()
                        .id(holding.getId())
                        .name(holding.getName())
                        .code(holding.getCode())
                        .assetCategory(holding.getAssetCategory())
                        .build())
                .series(points)
                .build();
    }

    // ======================================================================
    // 4.4 单持仓 vs 上期变化
    // ======================================================================

    public HoldingDiffDTO getHoldingDiff(String holdingId) {
        List<HoldingSnapshot> top2 = holdingSnapshotRepository.findTop2ByHoldingIdOrderBySnapshotDateDesc(holdingId);
        if (top2.isEmpty()) {
            // 无快照时不抛异常，返回空响应（与 getHoldingSeries 返回空 series 行为一致）
            return HoldingDiffDTO.builder()
                    .holdingId(holdingId)
                    .current(null)
                    .previous(null)
                    .marketValueChange(BigDecimal.ZERO)
                    .marketValueChangePct(BigDecimal.ZERO)
                    .sharesChange(BigDecimal.ZERO)
                    .sharesChangePct(BigDecimal.ZERO)
                    .pctOfTotalChange(BigDecimal.ZERO)
                    .build();
        }
        HoldingSnapshot current = top2.get(0);
        HoldingSnapshot previous = top2.size() >= 2 ? top2.get(1) : null;

        HoldingDiffDTO.SnapshotSummary currentSummary = HoldingDiffDTO.SnapshotSummary.builder()
                .date(current.getSnapshotDate())
                .marketValue(current.getMarketValue())
                .shares(current.getShares())
                .build();

        HoldingDiffDTO.SnapshotSummary previousSummary = previous != null
                ? HoldingDiffDTO.SnapshotSummary.builder()
                        .date(previous.getSnapshotDate())
                        .marketValue(previous.getMarketValue())
                        .shares(previous.getShares())
                        .build()
                : null;

        BigDecimal mvChange = previous != null
                ? current.getMarketValue().subtract(previous.getMarketValue())
                : BigDecimal.ZERO;
        BigDecimal mvChangePct = previous != null && previous.getMarketValue().compareTo(BigDecimal.ZERO) > 0
                ? mvChange.multiply(HUNDRED).divide(previous.getMarketValue(), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal sharesChange = previous != null
                ? current.getShares().subtract(previous.getShares())
                : BigDecimal.ZERO;
        BigDecimal sharesChangePct = previous != null && previous.getShares().compareTo(BigDecimal.ZERO) > 0
                ? sharesChange.multiply(HUNDRED).divide(previous.getShares(), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal pctChange = previous != null
                ? current.getPctOfTotal().subtract(previous.getPctOfTotal())
                : BigDecimal.ZERO;

        return HoldingDiffDTO.builder()
                .holdingId(holdingId)
                .current(currentSummary)
                .previous(previousSummary)
                .marketValueChange(mvChange)
                .marketValueChangePct(mvChangePct)
                .sharesChange(sharesChange)
                .sharesChangePct(sharesChangePct)
                .pctOfTotalChange(pctChange)
                .build();
    }

    // ======================================================================
    // 4.5 年化收益率（IRR）
    // ======================================================================

    /**
     * 仅对 us_stock/gold/dividend 持仓计算年化收益率。
     * IRR 边界：单笔 buy / holdingDays<1 / costBasis=0 → 返回 null。
     */
    public AnnualizedReturnDTO getAnnualizedReturn(String holdingId) {
        Holding holding = holdingRepository.findByIdAndDeletedFalse(holdingId)
                .orElseThrow(BusinessException::holdingNotFound);

        BigDecimal currentValue = holding.getMarketValue() == null ? BigDecimal.ZERO : holding.getMarketValue();
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalWithdrawn = BigDecimal.ZERO;

        // 非目标类别直接返回 null 年化
        if (holding.getAssetCategory() == null || !ANNUALIZED_CATEGORIES.contains(holding.getAssetCategory())) {
            return AnnualizedReturnDTO.builder()
                    .holdingId(holdingId)
                    .annualizedReturn(null)
                    .totalInvested(totalInvested)
                    .totalWithdrawn(totalWithdrawn)
                    .currentValue(currentValue)
                    .holdingDays(null)
                    .firstTransactionDate(null)
                    .irr(null)
                    .build();
        }

        List<Transaction> txList = transactionRepository.findByHoldingId(holdingId);
        if (txList.isEmpty()) {
            return buildEmptyAnnualized(holdingId, currentValue);
        }
        // 按日期升序
        txList.sort((a, b) -> a.getDate().compareTo(b.getDate()));

        LocalDate firstDate = txList.get(0).getDate();
        int holdingDays = (int) ChronoUnit.DAYS.between(firstDate, LocalDate.now());

        // 统计投入/回收
        for (Transaction t : txList) {
            if (t.getType() == TransactionType.buy) {
                totalInvested = totalInvested.add(t.getTotal());
            } else if (t.getType() == TransactionType.sell) {
                totalWithdrawn = totalWithdrawn.add(t.getTotal());
            }
        }

        // IRR 边界检查：少于 2 笔交易、或持有不足 7 天（年化意义不大）
        if (txList.size() < 2 || holdingDays < 7) {
            return AnnualizedReturnDTO.builder()
                    .holdingId(holdingId)
                    .annualizedReturn(null)
                    .totalInvested(totalInvested)
                    .totalWithdrawn(totalWithdrawn)
                    .currentValue(currentValue)
                    .holdingDays(holdingDays)
                    .firstTransactionDate(firstDate)
                    .irr(null)
                    .build();
        }
        BigDecimal costBasis = holding.getNetInvestment() == null ? BigDecimal.ZERO : holding.getNetInvestment();
        if (costBasis.compareTo(BigDecimal.ZERO) == 0) {
            return AnnualizedReturnDTO.builder()
                    .holdingId(holdingId)
                    .annualizedReturn(null)
                    .totalInvested(totalInvested)
                    .totalWithdrawn(totalWithdrawn)
                    .currentValue(currentValue)
                    .holdingDays(holdingDays)
                    .firstTransactionDate(firstDate)
                    .irr(null)
                    .build();
        }

        // 构建现金流序列：buy=流出(负)，sell=流入(正)，reinvest=净0，bonus_share=0
        // 终值 = 当前市值（最后一笔正现金流）
        List<Double> cashflows = new ArrayList<>();
        List<Double> timeWeights = new ArrayList<>();
        for (Transaction t : txList) {
            double tYears = ChronoUnit.DAYS.between(firstDate, t.getDate()) / 365.0;
            double cf;
            switch (t.getType()) {
                case buy:
                    cf = -t.getTotal().doubleValue();
                    break;
                case sell:
                    cf = t.getTotal().doubleValue();
                    break;
                case reinvest:
                    // 分红到账再投入，净现金流=0
                    cf = 0.0;
                    break;
                case bonus_share:
                default:
                    cf = 0.0;
                    break;
            }
            cashflows.add(cf);
            timeWeights.add(tYears);
        }
        // 加入终值（当前市值作为最后一笔正现金流，时间权重=持有年限）
        cashflows.add(currentValue.doubleValue());
        timeWeights.add(holdingDays / 365.0);

        double irr = calculateIRR(cashflows.stream().mapToDouble(Double::doubleValue).toArray(),
                timeWeights.stream().mapToDouble(Double::doubleValue).toArray());

        // IRR 未收敛（无解或现金流结构异常）→ 返回 null
        if (Double.isNaN(irr)) {
            return AnnualizedReturnDTO.builder()
                    .holdingId(holdingId)
                    .annualizedReturn(null)
                    .totalInvested(totalInvested)
                    .totalWithdrawn(totalWithdrawn)
                    .currentValue(currentValue)
                    .holdingDays(holdingDays)
                    .firstTransactionDate(firstDate)
                    .irr(null)
                    .build();
        }

        // 年化收益率 = (1 + irr)^(365/holdingDays) - 1
        double annualized = Math.pow(1 + irr, 365.0 / holdingDays) - 1;
        // 合理性保护：年化超出 [-95%, 1000%] 视为异常（如短期负收益被指数放大到 -100%）
        if (annualized < -0.95 || annualized > 10.0) {
            return AnnualizedReturnDTO.builder()
                    .holdingId(holdingId)
                    .annualizedReturn(null)
                    .totalInvested(totalInvested)
                    .totalWithdrawn(totalWithdrawn)
                    .currentValue(currentValue)
                    .holdingDays(holdingDays)
                    .firstTransactionDate(firstDate)
                    .irr(null)
                    .build();
        }
        BigDecimal annualizedPct = BigDecimal.valueOf(annualized * 100).setScale(2, RoundingMode.HALF_UP);
        BigDecimal irrBd = BigDecimal.valueOf(irr).setScale(6, RoundingMode.HALF_UP);

        return AnnualizedReturnDTO.builder()
                .holdingId(holdingId)
                .annualizedReturn(annualizedPct)
                .totalInvested(totalInvested)
                .totalWithdrawn(totalWithdrawn)
                .currentValue(currentValue)
                .holdingDays(holdingDays)
                .firstTransactionDate(firstDate)
                .irr(irrBd)
                .build();
    }

    /**
     * 二分法求解 IRR。
     * @param cashflows 现金流数组，正数=流入，负数=流出
     * @param timeWeights 时间权重数组（年为单位，t=0 表示第一笔）
     * @return IRR 原始值，如 0.0567 表示 5.67%；若未收敛返回 Double.NaN
     */
    private double calculateIRR(double[] cashflows, double[] timeWeights) {
        double low = -0.999, high = 10.0, mid = 0;
        double lastNpv = 0;
        for (int i = 0; i < 100; i++) {
            mid = (low + high) / 2;
            double npv = 0;
            for (int j = 0; j < cashflows.length; j++) {
                npv += cashflows[j] / Math.pow(1 + mid, timeWeights[j]);
            }
            lastNpv = npv;
            if (Math.abs(npv) < 0.01) break;
            if (npv > 0) low = mid;
            else high = mid;
        }
        // 收敛性检查：100 次迭代后 NPV 仍显著偏离 0，说明 IRR 无解或不稳定
        if (Math.abs(lastNpv) > 0.01) {
            return Double.NaN;
        }
        return mid;
    }

    private AnnualizedReturnDTO buildEmptyAnnualized(String holdingId, BigDecimal currentValue) {
        return AnnualizedReturnDTO.builder()
                .holdingId(holdingId)
                .annualizedReturn(null)
                .totalInvested(BigDecimal.ZERO)
                .totalWithdrawn(BigDecimal.ZERO)
                .currentValue(currentValue)
                .holdingDays(null)
                .firstTransactionDate(null)
                .irr(null)
                .build();
    }

    // ======================================================================
    // 辅助
    // ======================================================================

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

    /**
     * 查询快照记录列表（分页），按日期降序。
     */
    public Map<String, Object> listSnapshots(int page, int size) {
        int p = Math.max(page, 0);
        int s = Math.max(size, 1);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(p, s,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "snapshotDate"));
        org.springframework.data.domain.Page<HoldingSnapshot> pageResult = holdingSnapshotRepository.findAll(pageable);
        Map<String, Object> result = new HashMap<>();
        result.put("items", pageResult.getContent());
        result.put("total", pageResult.getTotalElements());
        result.put("page", p);
        result.put("pageSize", s);
        result.put("totalPages", pageResult.getTotalPages());
        return result;
    }
}
