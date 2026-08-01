package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.AnnualizedReturnDTO;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.entity.Transaction;
import com.fundtracker.model.enums.TransactionType;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 年化收益率（IRR）服务。
 * 职责：对 us_stock/gold/dividend 持仓计算年化收益率，含 IRR 边界检查和三层保护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnualizedReturnService {

    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;

    /** 仅这三类持仓参与年化收益率计算 */
    private static final List<String> ANNUALIZED_CATEGORIES = List.of("us_stock", "gold", "dividend");

    /**
     * 仅对 us_stock/gold/dividend 持仓计算年化收益率。
     * IRR 边界：单笔 buy / holdingDays<1 / costBasis=0 → 返回 null。
     * 三层保护：持有天数阈值、IRR 收敛检查、结果钳制（见项目规则）。
     */
    public AnnualizedReturnDTO getAnnualizedReturn(String holdingId, String userId) {
        Holding holding = holdingRepository.findByIdAndUserIdAndDeletedFalse(holdingId, userId)
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
}