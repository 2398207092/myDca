package com.fundtracker.service;

import com.fundtracker.model.dto.ValueChangeDTO;
import com.fundtracker.model.entity.AssetSnapshot;
import com.fundtracker.model.entity.FundNavRecord;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.entity.ManualAsset;
import com.fundtracker.model.entity.Transaction;
import com.fundtracker.model.enums.TransactionType;
import com.fundtracker.repository.AssetSnapshotRepository;
import com.fundtracker.repository.FundNavRecordRepository;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.repository.ManualAssetRepository;
import com.fundtracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValueChangeService {

    private final HoldingRepository holdingRepository;
    private final FundNavRecordRepository fundNavRecordRepository;
    private final TransactionRepository transactionRepository;
    private final ManualAssetRepository manualAssetRepository;
    private final AssetSnapshotRepository assetSnapshotRepository;

    public ValueChangeDTO getValueChange(String userId) {
        List<Holding> holdings = holdingRepository.findByUserIdAndDeletedFalseOrderByMarketValueDesc(userId);
        List<ManualAsset> manualAssets = manualAssetRepository.findByUserId(userId);

        if (holdings.isEmpty() && manualAssets.isEmpty()) {
            return ValueChangeDTO.builder()
                    .currentValue(BigDecimal.ZERO)
                    .periods(Map.of())
                    .build();
        }

        // 当前总市值 = 基金持仓 + 手动资产（现金/数字货币）
        BigDecimal currentValue = holdings.stream()
                .map(h -> h.getMarketValue() != null ? h.getMarketValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        for (ManualAsset ma : manualAssets) {
            if (ma.getAmount() != null) {
                currentValue = currentValue.add(ma.getAmount());
            }
        }

        // 按持仓聚合交易记录（升序，一次取回）
        Map<String, List<Transaction>> txByHolding = new HashMap<>();
        for (Transaction t : transactionRepository.findByUserIdOrderByDateDesc(userId)) {
            txByHolding.computeIfAbsent(t.getHoldingId(), k -> new ArrayList<>()).add(t);
        }
        for (List<Transaction> list : txByHolding.values()) {
            Collections.reverse(list); // descending → ascending
        }

        // 用户全部历史快照（升序），用于手动资产期初余额回溯
        List<AssetSnapshot> snapshots = assetSnapshotRepository.findByUserIdOrderByDateAsc(userId);

        LocalDate today = LocalDate.now();
        Map<String, ValueChangeDTO.PeriodChange> periods = new LinkedHashMap<>();
        periods.put("week", computePeriodChange(holdings, txByHolding, snapshots, manualAssets, today, 7));
        periods.put("month", computePeriodChange(holdings, txByHolding, snapshots, manualAssets, today, 30));
        periods.put("year", computePeriodChange(holdings, txByHolding, snapshots, manualAssets, today, 365));

        return ValueChangeDTO.builder()
                .currentValue(currentValue)
                .periods(periods)
                .build();
    }

    private ValueChangeDTO.PeriodChange computePeriodChange(
            List<Holding> holdings,
            Map<String, List<Transaction>> txByHolding,
            List<AssetSnapshot> snapshots,
            List<ManualAsset> manualAssets,
            LocalDate today, int daysAgo) {

        LocalDate pastDate = today.minusDays(daysAgo);
        BigDecimal totalBase = BigDecimal.ZERO;   // 收益率分母基准合计（期初市值 + 期间净投入）
        BigDecimal totalChange = BigDecimal.ZERO; // 真实变动合计
        List<ValueChangeDTO.HoldingDetail> details = new ArrayList<>();

        // ===== 基金类持仓：真实收益（期初市值 + 期间净投入） =====
        for (Holding h : holdings) {
            String code = h.getCode();
            BigDecimal shares = h.getShares() != null ? h.getShares() : BigDecimal.ZERO;
            BigDecimal currentMV = h.getMarketValue() != null ? h.getMarketValue() : BigDecimal.ZERO;
            List<Transaction> txs = txByHolding.getOrDefault(h.getId(), List.of());

            BigDecimal base;
            BigDecimal change;
            BigDecimal pastMV;
            String basis;

            if (code != null && !code.isBlank() && shares.compareTo(BigDecimal.ZERO) > 0 && !txs.isEmpty()) {
                // 有交易记录 → 用交易反推期初份额，算真实收益
                BigDecimal pastShares = sharesOnOrBefore(txs, pastDate);
                Optional<FundNavRecord> navOpt = fundNavRecordRepository
                        .findTopByFundCodeAndNavDateLessThanEqualOrderByNavDateDesc(code, pastDate);
                if (navOpt.isPresent()) {
                    BigDecimal pastNav = navOpt.get().getUnitNav();
                    pastMV = pastShares.multiply(pastNav).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal netInvest = netInvestmentAfter(txs, pastDate);
                    base = pastMV.add(netInvest);
                    change = currentMV.subtract(pastMV).subtract(netInvest);
                    basis = "fund";
                } else {
                    // 有交易但无期初净值 → 无法定位期初市值，暂按"当前值不变"处理
                    pastMV = currentMV;
                    base = currentMV;
                    change = BigDecimal.ZERO;
                    basis = "manual";
                }
            } else if (code != null && !code.isBlank() && shares.compareTo(BigDecimal.ZERO) > 0) {
                // 无流水但有净值 → 按净值估算（纯指数口径，明确标注）
                Optional<FundNavRecord> navOpt = fundNavRecordRepository
                        .findTopByFundCodeAndNavDateLessThanEqualOrderByNavDateDesc(code, pastDate);
                if (navOpt.isPresent()) {
                    BigDecimal pastNav = navOpt.get().getUnitNav();
                    pastMV = shares.multiply(pastNav).setScale(2, RoundingMode.HALF_UP);
                    base = pastMV;
                    change = currentMV.subtract(pastMV);
                    basis = "nav";
                } else {
                    pastMV = shares.multiply(
                            fundNavRecordRepository.findTopByFundCodeOrderByNavDateAsc(code)
                                    .map(FundNavRecord::getUnitNav).orElse(BigDecimal.ZERO))
                            .setScale(2, RoundingMode.HALF_UP);
                    base = pastMV.compareTo(BigDecimal.ZERO) > 0 ? pastMV : currentMV;
                    change = currentMV.subtract(pastMV);
                    basis = "nav";
                }
            } else {
                // 无净值无流水的持仓：期初=当前，变动 0
                pastMV = currentMV;
                base = currentMV;
                change = BigDecimal.ZERO;
                basis = "manual";
            }

            BigDecimal percent = base.compareTo(BigDecimal.ZERO) > 0
                    ? change.divide(base, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            if (currentMV.compareTo(BigDecimal.ZERO) > 0 || change.compareTo(BigDecimal.ZERO) != 0) {
                details.add(ValueChangeDTO.HoldingDetail.builder()
                        .holdingId(h.getId())
                        .name(h.getName())
                        .code(code)
                        .change(change.setScale(2, RoundingMode.HALF_UP))
                        .percent(percent.setScale(2, RoundingMode.HALF_UP))
                        .currentValue(currentMV)
                        .pastValue(pastMV.setScale(2, RoundingMode.HALF_UP))
                        .basis(basis)
                        .build());
            }

            totalChange = totalChange.add(change);
            totalBase = totalBase.add(base);
        }

        // ===== 手动资产（现金 / 数字货币）：无流水，用历史快照余额差 =====
        BigDecimal cashType = typeTotal(manualAssets, "cash");
        BigDecimal cryptoType = typeTotal(manualAssets, "crypto");
        addManualDetail(details, "cash", "现金", "cash", cashType, snapshots, pastDate);
        addManualDetail(details, "crypto", "数字货币", "crypto", cryptoType, snapshots, pastDate);

        // 总合：累加手动资产行
        for (ValueChangeDTO.HoldingDetail d : details) {
            if (d.getHoldingId().startsWith("manual:")) {
                totalChange = totalChange.add(d.getChange());
                totalBase = totalBase.add(d.getPastValue());
            }
        }

        BigDecimal totalPercent = totalBase.compareTo(BigDecimal.ZERO) > 0
                ? totalChange.divide(totalBase, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return ValueChangeDTO.PeriodChange.builder()
                .change(totalChange.setScale(2, RoundingMode.HALF_UP))
                .percent(totalPercent.setScale(2, RoundingMode.HALF_UP))
                .pastValue(totalBase.setScale(2, RoundingMode.HALF_UP))
                .details(details)
                .build();
    }

    private void addManualDetail(List<ValueChangeDTO.HoldingDetail> details,
                                 String type, String name, String typeLabel,
                                 BigDecimal currentType, List<AssetSnapshot> snapshots, LocalDate pastDate) {
        if (currentType.compareTo(BigDecimal.ZERO) <= 0) {
            return; // 该类型无余额，不显示
        }
        AssetSnapshot pastSnap = snapshotOnOrBefore(snapshots, pastDate);
        BigDecimal pastType;
        if (pastSnap == null) {
            // 无历史快照，无法追溯期初 → 本期变动记 0，避免把本金误认为收益
            pastType = currentType;
        } else {
            pastType = "cash".equals(type)
                    ? (pastSnap.getCashValue() != null ? pastSnap.getCashValue() : BigDecimal.ZERO)
                    : (pastSnap.getCryptoValue() != null ? pastSnap.getCryptoValue() : BigDecimal.ZERO);
        }
        BigDecimal change = currentType.subtract(pastType);
        BigDecimal percent = pastType.compareTo(BigDecimal.ZERO) > 0
                ? change.divide(pastType, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        details.add(ValueChangeDTO.HoldingDetail.builder()
                .holdingId("manual:" + type)
                .name(name)
                .code(typeLabel)
                .change(change.setScale(2, RoundingMode.HALF_UP))
                .percent(percent.setScale(2, RoundingMode.HALF_UP))
                .currentValue(currentType)
                .pastValue(pastType.setScale(2, RoundingMode.HALF_UP))
                .basis("manual")
                .build());
    }

    private BigDecimal typeTotal(List<ManualAsset> manualAssets, String type) {
        return manualAssets.stream()
                .filter(ma -> type.equals(ma.getType()))
                .map(ma -> ma.getAmount() != null ? ma.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 快照中"不晚于 date"的最新一条，无则返回 null */
    private AssetSnapshot snapshotOnOrBefore(List<AssetSnapshot> snapshots, LocalDate date) {
        AssetSnapshot best = null;
        for (AssetSnapshot s : snapshots) {
            if (s.getDate().isAfter(date)) break;
            best = s;
        }
        return best;
    }

    /** date 及之前的累计份额（buy/reinvest/bonus_share 加，sell 减）。txs 必须按日期升序 */
    private BigDecimal sharesOnOrBefore(List<Transaction> txs, LocalDate date) {
        BigDecimal shares = BigDecimal.ZERO;
        for (Transaction t : txs) {
            if (t.getDate().isAfter(date)) break;
            shares = shares.add(shareDelta(t));
        }
        return shares;
    }

    /** date 之后的净投入 = 期间买入总额 − 期间卖出总额（分红复投 re-invest 不计入新增本金） */
    private BigDecimal netInvestmentAfter(List<Transaction> txs, LocalDate date) {
        BigDecimal net = BigDecimal.ZERO;
        for (Transaction t : txs) {
            if (!t.getDate().isAfter(date)) continue;
            if (t.getType() == TransactionType.buy) {
                net = net.add(t.getTotal());
            } else if (t.getType() == TransactionType.sell) {
                net = net.subtract(t.getTotal());
            }
        }
        return net;
    }

    private BigDecimal shareDelta(Transaction t) {
        if (t.getType() == TransactionType.sell) {
            return t.getQuantity().negate();
        }
        return t.getQuantity();
    }
}