package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.HoldingDiffDTO;
import com.fundtracker.model.dto.HoldingSeriesDTO;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.entity.HoldingSnapshot;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.repository.HoldingSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 单持仓走势服务。
 * 职责：单持仓走势、单持仓 vs 上期变化。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingSeriesService {

    private final HoldingRepository holdingRepository;
    private final HoldingSnapshotRepository holdingSnapshotRepository;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * 单持仓走势。
     */
    public HoldingSeriesDTO getHoldingSeries(String holdingId, String range, String userId) {
        Holding holding = holdingRepository.findByIdAndUserIdAndDeletedFalse(holdingId, userId)
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

    /**
     * 单持仓 vs 上期变化。
     */
    public HoldingDiffDTO getHoldingDiff(String holdingId, String userId) {
        // 验证持仓属于当前用户
        holdingRepository.findByIdAndUserIdAndDeletedFalse(holdingId, userId)
                .orElseThrow(BusinessException::holdingNotFound);
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