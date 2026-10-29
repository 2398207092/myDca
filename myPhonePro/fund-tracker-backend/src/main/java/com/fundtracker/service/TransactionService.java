package com.fundtracker.service;

import com.fundtracker.event.TransactionChangedEvent;
import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.*;
import com.fundtracker.model.entity.DcaPlan;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.entity.Transaction;
import com.fundtracker.model.enums.HoldingType;
import com.fundtracker.model.enums.TransactionType;
import com.fundtracker.repository.DcaPlanRepository;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final HoldingRepository holdingRepository;
    private final DcaPlanRepository dcaPlanRepository;
    private final HoldingService holdingService;
    private final ManualAssetService manualAssetService;
    private final FundNavScrapeService fundNavScrapeService;
    private final ApplicationEventPublisher eventPublisher;

    public List<TransactionDTO> listTransactions(String holdingId, String type,
                                                  String dateFrom, String dateTo, String userId) {
        List<Transaction> transactions;
        if (holdingId != null && !holdingId.isEmpty()) {
            transactions = transactionRepository.findByHoldingIdAndUserIdOrderByDateDesc(holdingId, userId);
        } else {
            transactions = transactionRepository.findByUserIdOrderByDateDesc(userId);
        }

        return transactions.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public TransactionDTO createTransaction(CreateTransactionReq req, String userId) {
        Holding holding = holdingRepository.findByIdAndUserIdAndDeletedFalse(req.getHoldingId(), userId)
                .orElseThrow(BusinessException::holdingNotFound);

        TransactionType type = TransactionType.valueOf(req.getType());

        // 检查卖出份额是否足够
        if (type == TransactionType.sell) {
            if (req.getQuantity().compareTo(holding.getShares()) > 0) {
                throw BusinessException.insufficientShares();
            }
        }

        BigDecimal fee = req.getFee() != null ? req.getFee() : BigDecimal.ZERO;
        BigDecimal total = req.getQuantity().multiply(req.getPrice())
                .add(fee)
                .setScale(2, RoundingMode.HALF_UP);

        Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID().toString())
                .holdingId(req.getHoldingId())
                .userId(userId)
                .type(type)
                .date(LocalDate.parse(req.getDate()))
                .quantity(req.getQuantity())
                .price(req.getPrice())
                .fee(fee)
                .total(total)
                .source(req.getSource() != null ? req.getSource() : "manual")
                .dcaPlanId(req.getDcaPlanId())
                .build();

        transaction = transactionRepository.save(transaction);

        // 更新持仓份额
        switch (type) {
            case buy:
                holding.setShares(holding.getShares().add(req.getQuantity()));
                break;
            case sell:
                holding.setShares(holding.getShares().subtract(req.getQuantity()));
                break;
            case reinvest:
                holding.setShares(holding.getShares().add(req.getQuantity()));
                break;
            case bonus_share:
                holding.setShares(holding.getShares().add(req.getQuantity()));
                break;
        }

        // 重新计算预测分红（份额变化后需要重新计算）
        holdingService.calculatePredictedDividend(holding);

        // 成本更新：迁入持仓（成本手工录入）用增量累加，不从交易全量重算——
        // 否则迁入成本会被「只有交易部分」的成本覆盖（¥6,210 → ¥93.7 事故，2026-10-28）
        if (holding.isCostEstimated()) {
            if (type == TransactionType.buy) {
                holding.setCost(holding.getCost().add(total).setScale(2, RoundingMode.HALF_UP));
                holding.setNetInvestment(holding.getNetInvestment().add(total).setScale(2, RoundingMode.HALF_UP));
                if (holding.getShares().signum() > 0) {
                    holding.setCostPerShare(holding.getCost().divide(holding.getShares(), 4, RoundingMode.HALF_UP));
                }
            } else if (type == TransactionType.sell && holding.getCostPerShare() != null) {
                BigDecimal costReduce = holding.getCostPerShare().multiply(req.getQuantity()).setScale(2, RoundingMode.HALF_UP);
                holding.setCost(holding.getCost().subtract(costReduce).max(BigDecimal.ZERO));
                holding.setNetInvestment(holding.getNetInvestment().subtract(costReduce).max(BigDecimal.ZERO));
                // 全额卖出：剩余成本/每份成本一并清零，否则留下「0 份还有成本」的僵尸持仓
                if (holding.getShares().signum() <= 0) {
                    holding.setCost(BigDecimal.ZERO);
                    // 保留 costPerShare（最后已知每份成本），供后续删除/编辑卖单时回退使用
                    holding.setNetInvestment(BigDecimal.ZERO);
                }
            }
            holdingService.recalculateHoldingMetrics(holding, true);
        } else {
            holdingService.recalculateHoldingMetrics(holding);
        }

        // 用已有最新价更新市值（确保市值与份额一致，即使后续净值刷新失败）
        if (holding.getLatestPrice() != null && holding.getLatestPrice().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal marketValue = holding.getShares()
                    .multiply(holding.getLatestPrice())
                    .setScale(2, RoundingMode.HALF_UP);
            holding.setMarketValue(marketValue);
        }

        holdingRepository.save(holding);

        // 调整现金余额（异常会触发 @Transactional 回滚，交易也不创建）
        // 规则一：只联动人民币标的。加密币买卖动的是交易所 USDT 余额（定投由 DcaPlanService 单独扣数量），
        //         这里若扣人民币现金会造成跨币种双记账。
        // 规则二：优先扣持仓归属账户的现金；未归属账户时退回主现金（历史行为）。
        // 规则三：reinvest 不涉及现金（分红已到账，直接转份额）。
        // 规则四：卖出到账 = 成交额 − 手续费（此前误把手续费加进了到账金额）。
        if (holding.getType() != HoldingType.crypto) {
            if (type == TransactionType.buy) {
                manualAssetService.adjustCash(holding.getId(), holding.getAccountId(), total.negate(), userId);
            } else if (type == TransactionType.sell) {
                BigDecimal netProceeds = req.getQuantity().multiply(req.getPrice())
                        .subtract(fee)
                        .setScale(2, RoundingMode.HALF_UP);
                manualAssetService.adjustCash(holding.getId(), holding.getAccountId(), netProceeds, userId);
            }
        }

        // 刷新最新净值，更新市值 —— 本地优先：交易时不再调用外部净值 API
        // 净值仅在每日定时任务（FundNavScheduler 22:00）喂入 fund_nav_records，
        // 这里直接读取本地表；仅当本地无任何净值时才触发一次外部抓取（冷启动兜底）
        try {
            FundNavScrapeService.LatestNavResult navResult = fundNavScrapeService.getLatestNavFromDb(holding.getCode());
            if (navResult == null) {
                navResult = fundNavScrapeService.incrementalUpdate(holding.getCode());
            }
            if (navResult != null && navResult.unitNav() != null) {
                BigDecimal newMarketValue = holding.getShares()
                        .multiply(navResult.unitNav())
                        .setScale(2, RoundingMode.HALF_UP);
                holding.setMarketValue(newMarketValue);
                holding.setLatestPrice(navResult.unitNav());
                holding.setPriceDate(navResult.navDate());
                holdingRepository.save(holding);
                log.info("交易后刷新持仓 {} 市值: ¥{}", holding.getName(), newMarketValue);
            }
        } catch (Exception e) {
            log.warn("交易后刷新净值失败: {}", e.getMessage());
        }

        // 交易变更后发布事件触发分红事件同步
        eventPublisher.publishEvent(new TransactionChangedEvent(holding.getId(), userId));

        return toDTO(transaction);
    }

    @Transactional
    public TransactionDTO updateTransaction(String id, UpdateTransactionReq req, String userId) {
        Transaction tx = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(BusinessException::transactionNotFound);
        Holding holding = holdingRepository.findByIdAndUserIdAndDeletedFalse(tx.getHoldingId(), userId)
                .orElseThrow(BusinessException::holdingNotFound);

        // 记录编辑前的现金影响（编辑后差额一次性调整，保持与 create/delete 同一套公式）
        BigDecimal oldQuantity = tx.getQuantity();
        BigDecimal oldPrice = tx.getPrice();
        BigDecimal oldFee = tx.getFee();
        TransactionType oldType = tx.getType();
        // 迁入持仓需要按「旧影响 → 新影响」的差额增量调整，这里先固定旧 total
        BigDecimal oldTotal = oldQuantity.multiply(oldPrice).add(oldFee).setScale(2, RoundingMode.HALF_UP);

        // 超卖校验（迁入/非迁入通用）：先算出「剔除本笔后」的可用份额，再比新数量。
        // 放在改动任何字段之前，保证校验不过时实体一行未改（不依赖事务回滚兜底）
        TransactionType newType = req.getType() != null ? TransactionType.valueOf(req.getType()) : oldType;
        BigDecimal newQuantity = req.getQuantity() != null ? req.getQuantity() : oldQuantity;
        BigDecimal contributed = switch (oldType) {
            case buy, reinvest, bonus_share -> oldQuantity;
            case sell -> oldQuantity.negate();
        };
        // 前提：shares 与 Σ交易一致时公式精确；手工改过份额的迁入持仓以手工值为新基数，
        // 出现负数时钳到 0，避免错误文案输出「可卖 -0.00003」
        BigDecimal sharesExcludingThis = holding.getShares().subtract(contributed).max(BigDecimal.ZERO);
        if (newType == TransactionType.sell && newQuantity.compareTo(sharesExcludingThis) > 0) {
            throw BusinessException.invalidParam("卖出数量超过可卖份额（可卖 " + sharesExcludingThis + "）");
        }

        // 更新字段（仅非 null 字段）
        if (req.getType() != null) {
            tx.setType(TransactionType.valueOf(req.getType()));
        }
        if (req.getDate() != null) {
            tx.setDate(LocalDate.parse(req.getDate()));
        }
        if (req.getQuantity() != null) {
            tx.setQuantity(req.getQuantity());
        }
        if (req.getPrice() != null) {
            tx.setPrice(req.getPrice());
        }
        if (req.getFee() != null) {
            tx.setFee(req.getFee());
        }

        // 重新计算 total = 数量 × 单价 + 手续费
        BigDecimal total = tx.getQuantity().multiply(tx.getPrice())
                .add(tx.getFee())
                .setScale(2, RoundingMode.HALF_UP);
        tx.setTotal(total);

        transactionRepository.save(tx);

        // 现金联动：编辑交易也要同步现金，否则账永远对不平（差额 = 新影响 − 旧影响）
        if (holding.getType() != HoldingType.crypto) {
            BigDecimal oldImpact = cashImpact(oldType, oldQuantity, oldPrice, oldFee);
            BigDecimal newImpact = cashImpact(tx.getType(), tx.getQuantity(), tx.getPrice(), tx.getFee());
            BigDecimal delta = newImpact.subtract(oldImpact);
            if (delta.compareTo(BigDecimal.ZERO) != 0) {
                manualAssetService.adjustCash(holding.getId(), holding.getAccountId(), delta, userId);
            }
        }

        // 份额与成本重算
        if (holding.isCostEstimated()) {
            // 迁入持仓（手工录入份额/成本，无对应历史交易）：全量重算会把迁入份额和成本清零
            // （2026-10-29 事故：编辑一笔定投 → shares 归零、cost 6,186.81→46.84、总资产蒸发 6,047）
            // 正确做法：回退旧交易影响，再应用新交易影响
            BigDecimal cpsAtEdit = holding.getCostPerShare() != null
                    ? holding.getCostPerShare() : BigDecimal.ZERO;
            applyDeltaToMigratedHolding(holding, oldType, oldQuantity, oldTotal, cpsAtEdit, -1, false);
            applyDeltaToMigratedHolding(holding, tx.getType(), tx.getQuantity(), total, cpsAtEdit, 1, true);
            holdingService.calculatePredictedDividend(holding);
            holdingService.recalculateHoldingMetrics(holding, true);
        } else {
            recalculateSharesFromScratch(holding);
            holdingService.calculatePredictedDividend(holding);
            holdingService.recalculateHoldingMetrics(holding);
        }
        // 用已有最新价更新市值
        if (holding.getLatestPrice() != null && holding.getLatestPrice().compareTo(BigDecimal.ZERO) > 0) {
            holding.setMarketValue(holding.getShares()
                    .multiply(holding.getLatestPrice())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        holdingRepository.save(holding);

        // 交易变更后发布事件触发分红事件同步
        eventPublisher.publishEvent(new TransactionChangedEvent(holding.getId(), userId));

        return toDTO(tx);
    }

    /** 一笔交易对现金的影响：买入扣（额+费），卖出进（额−费），reinvest 等不动现金 */
    private BigDecimal cashImpact(TransactionType type, BigDecimal quantity, BigDecimal price, BigDecimal fee) {
        BigDecimal f = fee != null ? fee : BigDecimal.ZERO;
        BigDecimal gross = quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
        if (type == TransactionType.buy) {
            return gross.add(f).negate();
        }
        if (type == TransactionType.sell) {
            return gross.subtract(f);
        }
        return BigDecimal.ZERO;
    }

    /**
     * 迁入持仓（costEstimated=1）的份额/成本按单笔交易增量调整。
     * <p>注意：costPerShareAtEdit 取的是「编辑时刻」的每份成本，多次卖出/编辑叠加后
     * 回退量存在 ≤0.00005×份额 量级的近似误差（BTC 上 &lt;0.001 元），不可用于严格对账。
     *
     * @param sign +1 应用该笔交易，-1 回退该笔交易
     */
    private void applyDeltaToMigratedHolding(Holding holding, TransactionType type,
                                             BigDecimal quantity, BigDecimal total,
                                             BigDecimal costPerShareAtEdit, int sign,
                                             boolean recalcCps) {
        BigDecimal dq = quantity.multiply(BigDecimal.valueOf(sign));
        BigDecimal dt = total.multiply(BigDecimal.valueOf(sign));
        switch (type) {
            case buy:
                holding.setShares(holding.getShares().add(dq));
                holding.setCost(holding.getCost().add(dt));
                break;
            case sell:
                // 卖出按「每份成本 × 数量」减成本（与 createTransaction 同口径）。
                // 不能用 total（卖出所得）——那会让成本随卖价浮动，且可扣成负数
                BigDecimal costDelta = costPerShareAtEdit.multiply(quantity)
                        .multiply(BigDecimal.valueOf(sign));
                holding.setShares(holding.getShares().subtract(dq));
                holding.setCost(holding.getCost().subtract(costDelta).max(BigDecimal.ZERO));
                break;
            case reinvest:
            case bonus_share:
                holding.setShares(holding.getShares().add(dq));
                break;
        }
        if (holding.getShares().signum() <= 0) {
            // 全额卖出：份额、成本、净投入一并清零（与 CostCalculator 对 shares==0 的行为一致），
            // 否则会留下「0 份但还有成本」的僵尸持仓
            holding.setShares(BigDecimal.ZERO);
            holding.setCost(BigDecimal.ZERO);
            // 保留 costPerShare 作为「最后已知每份成本」：清仓后若删除/修改该笔卖单，
            // 回退公式 costDelta = cps × qty 需要它；清零会导致成本永久丢失（2026-10-29 复核拦截）
            holding.setNetInvestment(BigDecimal.ZERO);
            return;
        }
        // 每份成本只在「最终状态」重算一次：回退阶段重算会把原始 cps（如 542827.3239）
        // 带成 4 位近似值（542828.0000），清仓后保留的就不是原值了
        if (recalcCps && holding.getCost().signum() > 0) {
            holding.setCostPerShare(holding.getCost()
                    .divide(holding.getShares(), 4, RoundingMode.HALF_UP));
        }
        // 迁入持仓的净投入始终等于成本（与 HoldingService 的 skip 分支一致）
        holding.setNetInvestment(holding.getCost());
    }

    private void recalculateSharesFromScratch(Holding holding) {
        List<Transaction> allTx = transactionRepository.findByHoldingId(holding.getId());
        BigDecimal shares = BigDecimal.ZERO;
        for (Transaction t : allTx) {
            switch (t.getType()) {
                case buy:
                case reinvest:
                case bonus_share:
                    shares = shares.add(t.getQuantity());
                    break;
                case sell:
                    shares = shares.subtract(t.getQuantity());
                    break;
            }
        }
        holding.setShares(shares.max(BigDecimal.ZERO));
    }

    @Transactional
    public void deleteTransaction(String id, String userId) {
        Transaction transaction = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(BusinessException::transactionNotFound);
        String holdingId = transaction.getHoldingId();
        BigDecimal txTotal = transaction.getTotal();
        TransactionType txType = transaction.getType();
        Holding holding = holdingRepository.findByIdAndUserIdAndDeletedFalse(holdingId, userId)
                .orElseThrow(BusinessException::holdingNotFound);

        transactionRepository.delete(transaction);

        // P1-20：删除定投交易必须回退计划累计计数器，否则累计期数/投入永久失真且不可自愈。
        // last_executed_at / next_execution_date 不动——执行进度本身没有被撤销。
        if ("dca".equals(transaction.getSource()) && transaction.getDcaPlanId() != null) {
            dcaPlanRepository.findById(transaction.getDcaPlanId()).ifPresent(plan -> {
                plan.setTotalExecutions(Math.max(0, plan.getTotalExecutions() - 1));
                plan.setTotalInvested(plan.getTotalInvested()
                        .subtract(txTotal).max(BigDecimal.ZERO));
                plan.setTotalShares(plan.getTotalShares()
                        .subtract(transaction.getQuantity()).max(BigDecimal.ZERO));
                dcaPlanRepository.save(plan);
                log.info("回退定投计划计数器: plan={}, 期数={}, 投入={}, 份额={}",
                        plan.getId(), plan.getTotalExecutions(), plan.getTotalInvested(), plan.getTotalShares());
            });
        }

        // 现金反向调整：与 createTransaction 对称（同一条规则，见 createTransaction 注释）。
        // 加密币不动人民币现金；reinvest 删除也不该把现金加回来（当初就没扣）。
        if (holding.getType() != HoldingType.crypto) {
            if (txType == TransactionType.buy) {
                manualAssetService.adjustCash(holdingId, holding.getAccountId(), txTotal, userId);
            } else if (txType == TransactionType.sell) {
                BigDecimal fee = transaction.getFee() != null ? transaction.getFee() : BigDecimal.ZERO;
                BigDecimal netProceeds = transaction.getQuantity().multiply(transaction.getPrice())
                        .subtract(fee)
                        .setScale(2, RoundingMode.HALF_UP);
                manualAssetService.adjustCash(holdingId, holding.getAccountId(), netProceeds.negate(), userId);
            }
        }

        // 删除后重新计算份额和指标
        if (holding.isCostEstimated()) {
            // 迁入持仓（成本/份额手工录入）：份额与成本按该笔交易增量回退（不从全部交易重算），
            // 否则删一笔定投会把手工维护的迁入成本一起抹掉
            if (txType == TransactionType.buy) {
                holding.setShares(holding.getShares().subtract(transaction.getQuantity()).max(BigDecimal.ZERO));
                holding.setCost(holding.getCost().subtract(txTotal).max(BigDecimal.ZERO));
                holding.setNetInvestment(holding.getNetInvestment().subtract(txTotal).max(BigDecimal.ZERO));
            } else if (txType == TransactionType.sell) {
                // 删除卖出交易要同时恢复份额与被扣掉的成本（口径同 create：每份成本 × 数量）
                holding.setShares(holding.getShares().add(transaction.getQuantity()));
                BigDecimal cps = holding.getCostPerShare() != null
                        ? holding.getCostPerShare() : BigDecimal.ZERO;
                holding.setCost(holding.getCost()
                        .add(cps.multiply(transaction.getQuantity()))
                        .setScale(2, RoundingMode.HALF_UP));
                // 净投入必须与成本保持相等（V-05 不变量）；否则快照任务会拍到 net≠cost 的错值
                holding.setNetInvestment(holding.getCost());
            }
            if (holding.getShares().signum() > 0 && holding.getCost().signum() > 0) {
                holding.setCostPerShare(holding.getCost().divide(holding.getShares(), 4, RoundingMode.HALF_UP));
            }
            log.info("删除交易: 迁入持仓 {} 按增量回退（份额={}，成本=¥{}）", holdingId, holding.getShares(), holding.getCost());
            if (holding.getLatestPrice() != null && holding.getLatestPrice().compareTo(BigDecimal.ZERO) > 0) {
                holding.setMarketValue(holding.getShares()
                        .multiply(holding.getLatestPrice())
                        .setScale(2, RoundingMode.HALF_UP));
            }
        } else {
            recalculateSharesFromScratch(holding);
            holdingService.calculatePredictedDividend(holding);
            holdingService.recalculateHoldingMetrics(holding);
            // 用已有最新价更新市值
            if (holding.getLatestPrice() != null && holding.getLatestPrice().compareTo(BigDecimal.ZERO) > 0) {
                holding.setMarketValue(holding.getShares()
                        .multiply(holding.getLatestPrice())
                        .setScale(2, RoundingMode.HALF_UP));
            }
        }
        holdingRepository.save(holding);

        // 交易变更后发布事件触发分红事件同步
        eventPublisher.publishEvent(new TransactionChangedEvent(holdingId, userId));
    }

    /**
     * 获取指定持仓的首笔交易日期
     */
    public LocalDate getFirstTransactionDate(String holdingId) {
        return transactionRepository.findEarliestTransactionDateByHoldingId(holdingId)
                .orElse(null);
    }

    /**
     * 计算指定持仓在特定日期的实时份额
     * 遍历该持仓所有交易（按日期升序），累加到目标日期为止
     */
    public BigDecimal calculateSharesAtDate(String holdingId, LocalDate date) {
        List<Transaction> allTx = transactionRepository.findByHoldingIdOrderByDateAsc(holdingId);
        BigDecimal shares = BigDecimal.ZERO;
        for (Transaction t : allTx) {
            if (t.getDate().isAfter(date)) break;
            switch (t.getType()) {
                case buy:
                case reinvest:
                case bonus_share:
                    shares = shares.add(t.getQuantity());
                    break;
                case sell:
                    shares = shares.subtract(t.getQuantity());
                    break;
            }
        }
        return shares.max(BigDecimal.ZERO);
    }

    private TransactionDTO toDTO(Transaction tx) {
        return TransactionDTO.builder()
                .id(tx.getId())
                .holdingId(tx.getHoldingId())
                .type(tx.getType().name())
                .date(tx.getDate().toString())
                .quantity(tx.getQuantity())
                .price(tx.getPrice())
                .fee(tx.getFee())
                .total(tx.getTotal())
                .source(tx.getSource())
                .dcaPlanId(tx.getDcaPlanId())
                .build();
    }
}
