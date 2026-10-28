package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.*;
import com.fundtracker.model.entity.DcaPlan;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.entity.ManualAsset;
import com.fundtracker.model.enums.DcaFrequency;
import com.fundtracker.model.enums.DcaPlanStatus;
import com.fundtracker.model.enums.HoldingType;
import com.fundtracker.repository.DcaPlanRepository;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.repository.ManualAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DcaPlanService {

    private final DcaPlanRepository dcaPlanRepository;
    private final HoldingRepository holdingRepository;
    private final TransactionService transactionService;
    private final FundNavScrapeService fundNavScrapeService;
    private final TradingCalendar tradingCalendar;
    private final ManualAssetRepository manualAssetRepository;
    private final ExchangeRateService exchangeRateService;

    /**
     * 根据持仓类型推断交易市场
     */
    public static String inferTradingMarket(Holding holding) {
        HoldingType type = holding.getType();
        switch (type) {
            case 美股:
                return "us";
            case crypto:
                // 加密币 365 天交易，交易日历对 crypto 直接放行（含周末）
                return "crypto";
            default:
                return "china";
        }
    }

    /**
     * 计算指定月份的定投预算
     */
    public DcaBudgetVO calculateBudget(int year, int month, String userId) {
        List<DcaPlan> activePlans = dcaPlanRepository.findByUserId(userId).stream()
                .filter(p -> DcaPlanStatus.active == p.getStatus())
                .collect(Collectors.toList());

        if (activePlans.isEmpty()) {
            return DcaBudgetVO.builder()
                    .month(year + "-" + String.format("%02d", month))
                    .tradingDays(0)
                    .totalAmount(BigDecimal.ZERO)
                    .plans(List.of())
                    .build();
        }

        // 交易日数按市场分别取：基金走交易日（跳周末节假日），加密币 365 天全算
        int chinaDays = tradingCalendar.countTradingDaysInMonth(year, month, "china");
        int cryptoDays = tradingCalendar.countTradingDaysInMonth(year, month, "crypto");
        BigDecimal total = BigDecimal.ZERO;
        List<DcaBudgetVO.PlanBudgetItem> items = new java.util.ArrayList<>();

        for (DcaPlan plan : activePlans) {
            Holding holding = holdingRepository.findByIdAndUserIdAndDeletedFalse(plan.getHoldingId(), userId).orElse(null);
            boolean planIsCrypto = holding != null && holding.getType() == HoldingType.crypto;
            int tradingDays = planIsCrypto ? cryptoDays : chinaDays;
            int executions = estimateExecutionsInMonth(plan, year, month, tradingDays);
            BigDecimal budget = plan.getAmount().multiply(BigDecimal.valueOf(executions));
            total = total.add(budget);
            items.add(DcaBudgetVO.PlanBudgetItem.builder()
                    .holdingName(holding != null ? holding.getName() : "未知")
                    .frequency(plan.getFrequency().name())
                    .amount(plan.getAmount())
                    .executions(executions)
                    .budgetAmount(budget)
                    .build());
        }

        return DcaBudgetVO.builder()
                .month(year + "-" + String.format("%02d", month))
                .tradingDays(chinaDays)
                .totalAmount(total)
                .plans(items)
                .build();
    }

    /**
     * 估算某个月份的执行次数
     */
    private int estimateExecutionsInMonth(DcaPlan plan, int year, int month, int tradingDays) {
        switch (plan.getFrequency()) {
            case daily:
                // 每日只会跳过非交易日
                return tradingDays;
            case weekly: {
                // 该月指定星期几落在交易日上的次数
                if (plan.getDay() == null) return 4;
                int targetDayOfWeek = plan.getDay(); // 1=周一..7=周日
                int count = 0;
                LocalDate start = LocalDate.of(year, month, 1);
                LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
                for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                    if (d.getDayOfWeek().getValue() == targetDayOfWeek
                            && tradingCalendar.isTradingDay(d, plan.getTradingMarket())) {
                        count++;
                    }
                }
                return count;
            }
            case biweekly:
                // 双周固定约 2 次
                return 2;
            case monthly:
                // 每月固定 1 次
                return 1;
            default:
                return 0;
        }
    }

    /**
     * 计算下次执行日期
     */
    private LocalDate calculateNextExecutionDate(LocalDate fromDate, DcaFrequency frequency, Integer day, String market) {
        LocalDate rawDate;
        switch (frequency) {
            case daily:
                rawDate = fromDate.plusDays(1);
                break;
            case weekly:
                rawDate = fromDate.plusWeeks(1);
                if (day != null) {
                    // day=1周一, day=7周日, 调整到指定周几
                    int targetDayOfWeek = day;
                    int currentDow = rawDate.getDayOfWeek().getValue();
                    rawDate = rawDate.plusDays(targetDayOfWeek - currentDow);
                }
                break;
            case biweekly:
                rawDate = fromDate.plusWeeks(2);
                if (day != null) {
                    int targetDayOfWeek = day;
                    int currentDow = rawDate.getDayOfWeek().getValue();
                    rawDate = rawDate.plusDays(targetDayOfWeek - currentDow);
                }
                break;
            case monthly:
                rawDate = fromDate.plusMonths(1);
                if (day != null) {
                    int targetDay = Math.min(day, rawDate.lengthOfMonth());
                    rawDate = rawDate.withDayOfMonth(targetDay);
                }
                break;
            default:
                rawDate = fromDate.plusDays(1);
                break;
        }
        // 顺延到下一个交易日
        return tradingCalendar.nextTradingDay(rawDate, market);
    }

    /**
     * 计算「下一个符合指定频率与扣款日的日期」（严格晚于 today）。
     *
     * <p><b>与 {@link #calculateNextExecutionDate} 的区别（重要）</b>：
     * 后者的语义是"从某日起加一个周期"，用于执行完一期后推进下一期；
     * 本方法的语义是"以今天为基准，找下一个符合当前设置的日子"，用于编辑频率/扣款日后的重算。
     * 编辑场景若误用前者会晚一整个周期 —— 例如今天是周二（10/13），把频率改为「每周三」，
     * 前者返回 10/21，而正确答案是 10/14。
     *
     * @param today     基准日（结果严格晚于该日）
     * @param frequency 频率
     * @param day       daily 传 null；weekly/biweekly 传 1~7（周一~周日）；monthly 传 1~31
     */
    static LocalDate nextOccurrence(LocalDate today, DcaFrequency frequency, Integer day) {
        switch (frequency) {
            case daily:
                return today.plusDays(1);
            case weekly: {
                if (day == null) return today.plusWeeks(1);
                // 今天当天不算，顺延到下一个同星期几
                int delta = day - today.getDayOfWeek().getValue();
                if (delta <= 0) delta += 7;
                return today.plusDays(delta);
            }
            case biweekly:
                // 双周没有独立锚点，沿用「今天 + 2 周」
                return today.plusWeeks(2);
            case monthly: {
                if (day == null) return today.plusMonths(1);
                // 本月该日未到就用本月，否则顺延到下月；月末做钳制，避免 2/30 这类非法日期
                LocalDate thisMonth = today.withDayOfMonth(Math.min(day, today.lengthOfMonth()));
                if (thisMonth.isAfter(today)) return thisMonth;
                LocalDate nextMonth = today.plusMonths(1);
                return nextMonth.withDayOfMonth(Math.min(day, nextMonth.lengthOfMonth()));
            }
            default:
                return today.plusDays(1);
        }
    }

    /** 解析频率字符串；非法值抛业务异常，避免 IllegalArgumentException 冒泡成 500 */
    private static DcaFrequency parseFrequency(String raw) {
        try {
            return DcaFrequency.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw BusinessException.invalidParam("定投频率无效: " + raw);
        }
    }

    /** 解析状态字符串；非法值抛业务异常 */
    private static DcaPlanStatus parseStatus(String raw) {
        try {
            return DcaPlanStatus.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw BusinessException.invalidParam("无效的状态: " + raw);
        }
    }

    /** 按频率校验扣款日范围 */
    private static void validateDay(DcaFrequency frequency, Integer day) {
        if (day == null || frequency == DcaFrequency.daily) return;
        if (frequency == DcaFrequency.weekly || frequency == DcaFrequency.biweekly) {
            if (day < 1 || day > 7) {
                throw BusinessException.invalidParam(
                        "频率为每周/双周时，扣款日需为 1~7（周一~周日），当前为 " + day);
            }
        } else if (frequency == DcaFrequency.monthly) {
            if (day < 1 || day > 31) {
                throw BusinessException.invalidParam(
                        "频率为每月时，扣款日需为 1~31，当前为 " + day);
            }
        }
    }

    @Transactional
    public DcaPlanVO createPlan(CreateDcaPlanReq req, String userId) {
        Holding holding = holdingRepository.findByIdAndUserIdAndDeletedFalse(req.getHoldingId(), userId)
                .orElseThrow(BusinessException::holdingNotFound);

        DcaFrequency frequency = parseFrequency(req.getFrequency());
        validateDay(frequency, req.getDay());

        String market = inferTradingMarket(holding);
        LocalDate today = LocalDate.now();
        // 首期执行日：明天开始的第一个交易日
        LocalDate nextExec = tradingCalendar.nextTradingDay(today.plusDays(1), market);

        DcaPlan plan = DcaPlan.builder()
                .id(UUID.randomUUID().toString())
                .holdingId(req.getHoldingId())
                .userId(userId)
                .amount(req.getAmount())
                .frequency(frequency)
                .day(req.getDay())
                .tradingMarket(market)
                .accountId(req.getAccountId())
                .status(DcaPlanStatus.active)
                .totalInvested(BigDecimal.ZERO)
                .totalShares(BigDecimal.ZERO)
                .totalExecutions(0)
                .nextExecutionDate(nextExec)
                .startedAt(today)
                .build();

        plan = dcaPlanRepository.save(plan);
        log.info("创建定投计划: holding={}, 金额={}, 频率={}, market={}",
                holding.getName(), req.getAmount(), req.getFrequency(), market);

        return toVO(plan, holding);
    }

    public List<DcaPlanVO> listPlans(String holdingId, String userId) {
        List<DcaPlan> plans;
        if (holdingId != null && !holdingId.isEmpty()) {
            plans = dcaPlanRepository.findByHoldingIdAndUserIdOrderByCreatedAtDesc(holdingId, userId);
        } else {
            plans = dcaPlanRepository.findByUserId(userId);
        }
        return plans.stream()
                .map(plan -> {
                    Holding h = holdingRepository.findByIdAndUserIdAndDeletedFalse(plan.getHoldingId(), userId).orElse(null);
                    return toVO(plan, h);
                })
                .collect(Collectors.toList());
    }

    public DcaPlanVO getPlan(String id, String userId) {
        DcaPlan plan = dcaPlanRepository.findByIdAndUserId(id, userId)
                .orElseThrow(BusinessException::planNotFound);
        Holding h = holdingRepository.findByIdAndUserIdAndDeletedFalse(plan.getHoldingId(), userId).orElse(null);
        return toVO(plan, h);
    }

    @Transactional
    public DcaPlanVO updatePlan(String id, UpdateDcaPlanReq req, String userId) {
        DcaPlan plan = dcaPlanRepository.findByIdAndUserId(id, userId)
                .orElseThrow(BusinessException::planNotFound);

        boolean scheduleChanged = false;

        // --- 频率 ---
        if (req.getFrequency() != null) {
            DcaFrequency newFrequency = parseFrequency(req.getFrequency());
            if (plan.getFrequency() != newFrequency) {
                plan.setFrequency(newFrequency);
                scheduleChanged = true;
            }
            // 切到「每日」时清掉扣款日（每日无扣款日概念）
            if (newFrequency == DcaFrequency.daily && plan.getDay() != null) {
                plan.setDay(null);
                scheduleChanged = true;
            }
        }

        // --- 扣款日（每日频率下忽略）---
        if (req.getDay() != null && plan.getFrequency() != DcaFrequency.daily) {
            if (!req.getDay().equals(plan.getDay())) {
                plan.setDay(req.getDay());
                scheduleChanged = true;
            }
        }

        // --- 扣款账户：空串 = 清除关联，非空 = 设置 ---
        if (req.getAccountId() != null) {
            plan.setAccountId(req.getAccountId().isBlank() ? null : req.getAccountId());
        }

        // --- 每期金额 ---
        if (req.getAmount() != null) {
            plan.setAmount(req.getAmount());
        }

        // 校验最终状态下的扣款日合法性（放在频率/扣款日都应用之后）
        validateDay(plan.getFrequency(), plan.getDay());

        // --- 状态 ---
        if (req.getStatus() != null) {
            DcaPlanStatus status = parseStatus(req.getStatus());
            switch (status) {
                case paused:
                    plan.setStatus(DcaPlanStatus.paused);
                    log.info("定投计划 {} 已暂停", id);
                    break;
                case active:
                    plan.setStatus(DcaPlanStatus.active);
                    plan.setNextExecutionDate(
                            tradingCalendar.nextTradingDay(LocalDate.now().plusDays(1), plan.getTradingMarket())
                    );
                    log.info("定投计划 {} 已恢复", id);
                    break;
                case ended:
                    plan.setStatus(DcaPlanStatus.ended);
                    plan.setEndedAt(LocalDate.now());
                    log.info("定投计划 {} 已终止", id);
                    break;
                default:
                    throw BusinessException.invalidParam("无效的状态: " + req.getStatus());
            }
        }

        // --- 频率/扣款日变更 → 以「今天」为基准重算下次执行日 ---
        // 仅在 active 时重算：暂停/终止的计划该字段无意义（恢复时上面已重算）
        if (scheduleChanged && plan.getStatus() == DcaPlanStatus.active) {
            LocalDate base = nextOccurrence(LocalDate.now(), plan.getFrequency(), plan.getDay());
            LocalDate nextExec = tradingCalendar.nextTradingDay(base, plan.getTradingMarket());
            plan.setNextExecutionDate(nextExec);
            log.info("定投计划 {} 频率/扣款日变更，下次执行日重算为 {}（基准 {}）", id, nextExec, base);
        }

        plan = dcaPlanRepository.save(plan);
        Holding h = holdingRepository.findByIdAndUserIdAndDeletedFalse(plan.getHoldingId(), userId).orElse(null);
        return toVO(plan, h);
    }

    @Transactional
    public void deletePlan(String id, String userId) {
        DcaPlan plan = dcaPlanRepository.findByIdAndUserId(id, userId)
                .orElseThrow(BusinessException::planNotFound);
        // 物理删除计划，关联的 dca 交易保留
        dcaPlanRepository.delete(plan);
        log.info("删除定投计划 {} (关联交易已保留)", id);
    }

    /**
     * 执行一期定投
     */
    @Transactional
    public DcaExecutionResultVO executePlan(String id, String userId) {
        DcaPlan plan = dcaPlanRepository.findByIdAndUserId(id, userId)
                .orElseThrow(BusinessException::planNotFound);

        if (!"active".equals(plan.getStatus().name())) {
            throw BusinessException.invalidParam("定投计划已暂停或终止，无法执行");
        }

        Holding holding = holdingRepository.findByIdAndUserIdAndDeletedFalse(plan.getHoldingId(), userId)
                .orElseThrow(BusinessException::holdingNotFound);

        LocalDate today = LocalDate.now();
        boolean isCrypto = holding.getType() == HoldingType.crypto;

        BigDecimal price;      // 人民币单价（入账口径）
        BigDecimal quantity;   // 份额 / 币数
        BigDecimal fee;
        BigDecimal amountCny;  // 本期人民币支出（扣款用）
        BigDecimal amountUsdt = null;
        FundNavScrapeService.LatestNavResult nav = null;

        if (isCrypto) {
            // 加密币定投：amount 的单位是 USDT，按当时 USDT 价折算币数，人民币成本 = USDT × 汇率
            BigDecimal usdtPrice = holding.getQuotePrice();
            if (usdtPrice == null || usdtPrice.signum() <= 0) {
                throw BusinessException.invalidParam("无法获取 " + holding.getName() + " 的 USDT 价格，请先刷新价格后重试");
            }
            BigDecimal usdCny = exchangeRateService.listAll().stream()
                    .filter(r -> "USD/CNY".equals(r.getPair()))
                    .map(ExchangeRateDTO::getRate)
                    .findFirst()
                    .orElse(null);
            if (usdCny == null) {
                throw BusinessException.bizError("汇率获取失败，无法执行加密币定投，请稍后重试");
            }
            price = usdtPrice.multiply(usdCny).setScale(4, RoundingMode.HALF_UP);
            quantity = plan.getAmount().divide(usdtPrice, 8, RoundingMode.HALF_UP);
            // 买入手续费已在交易所侧体现，这里不重复计算
            fee = BigDecimal.ZERO;
            amountCny = plan.getAmount().multiply(usdCny).setScale(2, RoundingMode.HALF_UP);
            amountUsdt = plan.getAmount();
        } else {
            // 获取最新可用净值
            nav = fundNavScrapeService.getLatestNavBefore(holding.getCode(), today);
            if (nav == null || nav.unitNav() == null || nav.unitNav().compareTo(BigDecimal.ZERO) <= 0) {
                throw BusinessException.invalidParam("无法获取基金 " + holding.getCode() + " 的净值数据，请确认净值已更新");
            }

            // 手续费 = 定投金额 × 持仓买入费率%，净额用于申购份额
            BigDecimal amount = plan.getAmount();
            BigDecimal feeRate = holding.getBuyFeeRate() != null ? holding.getBuyFeeRate() : new BigDecimal("0.15");
            fee = amount.multiply(feeRate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal netPrincipal = amount.subtract(fee).max(BigDecimal.ZERO);

            // 计算份额 = 净额 / 净值
            quantity = netPrincipal.divide(nav.unitNav(), 4, RoundingMode.HALF_UP);
            price = nav.unitNav();
            amountCny = plan.getAmount();
        }

        // 使用 TransactionService 创建买入交易
        CreateTransactionReq txReq = new CreateTransactionReq();
        txReq.setHoldingId(plan.getHoldingId());
        txReq.setType("buy");
        txReq.setDate(today.toString());
        txReq.setQuantity(quantity);
        txReq.setPrice(price);
        txReq.setFee(fee);
        txReq.setSource("dca");
        txReq.setDcaPlanId(id);

        TransactionDTO txDto = transactionService.createTransaction(txReq, userId);

        // 扣款：从计划关联的账户扣现金（基金）或 USDT（加密币）
        deductForExecution(plan, holding, userId, amountCny, amountUsdt);

        // 更新计划统计
        plan.setTotalInvested(plan.getTotalInvested().add(plan.getAmount()));
        plan.setTotalShares(plan.getTotalShares().add(quantity));
        plan.setTotalExecutions(plan.getTotalExecutions() + 1);
        plan.setLastExecutedAt(LocalDateTime.now());

        // 推进下次执行日期
        LocalDate nextExec = calculateNextExecutionDate(today, plan.getFrequency(), plan.getDay(), plan.getTradingMarket());
        plan.setNextExecutionDate(nextExec);
        dcaPlanRepository.save(plan);

        log.info("定投执行成功: plan={}, 金额={}, 份额={}, 单价={}, 下次执行={}",
                id, plan.getAmount(), quantity, price, nextExec);

        return DcaExecutionResultVO.builder()
                .transactionId(txDto.getId())
                .amount(plan.getAmount())
                .quantity(quantity)
                .navPrice(price)
                .navDate(isCrypto ? null : nav.navDate())
                .executionDate(today)
                .holdingName(holding.getName())
                .holdingCode(holding.getCode())
                .build();
    }

    /**
     * 定投扣款：从计划关联的账户扣现金（基金）或 USDT 数量（加密币）。
     * 找不到可扣资产时只记警告，不阻断执行——记账软件不应因账户余额不足而中断自动计划。
     */
    private void deductForExecution(DcaPlan plan, Holding holding, String userId,
                                    BigDecimal amountCny, BigDecimal amountUsdt) {
        if (plan.getAccountId() == null || plan.getAccountId().isBlank()) {
            return; // 未关联账户 = 纯记账
        }
        List<ManualAsset> assets = manualAssetRepository.findByUserIdAndAccountId(userId, plan.getAccountId());
        boolean isCrypto = holding.getType() == HoldingType.crypto;
        for (ManualAsset a : assets) {
            if (isCrypto && "crypto".equals(a.getType())) {
                BigDecimal q = a.getQuantity() != null ? a.getQuantity() : BigDecimal.ZERO;
                a.setQuantity(q.subtract(amountUsdt).setScale(8, RoundingMode.HALF_UP));
                if (a.getUnitPrice() != null) {
                    a.setAmount(a.getQuantity().multiply(a.getUnitPrice()).setScale(2, RoundingMode.HALF_UP));
                }
                manualAssetRepository.save(a);
                log.info("定投扣款: 计划={}, 账户资产={}, 扣减 {} USDT", plan.getId(), a.getName(), amountUsdt);
                return;
            }
            if (!isCrypto && "cash".equals(a.getType())) {
                a.setAmount(a.getAmount().subtract(amountCny).setScale(2, RoundingMode.HALF_UP));
                manualAssetRepository.save(a);
                log.info("定投扣款: 计划={}, 账户资产={}, 扣减 ¥{}", plan.getId(), a.getName(), amountCny);
                return;
            }
        }
        log.warn("定投扣款未找到可扣资产: 计划={}, 账户={}（该账户下没有对应的现金/加密资产），仅记账未扣款",
                plan.getId(), plan.getAccountId());
    }

    private DcaPlanVO toVO(DcaPlan plan, Holding holding) {
        return DcaPlanVO.builder()
                .id(plan.getId())
                .holdingId(plan.getHoldingId())
                .holdingName(holding != null ? holding.getName() : null)
                .holdingCode(holding != null ? holding.getCode() : null)
                .amount(plan.getAmount())
                .frequency(plan.getFrequency().name())
                .day(plan.getDay())
                .tradingMarket(plan.getTradingMarket())
                .accountId(plan.getAccountId())
                .status(plan.getStatus().name())
                .totalInvested(plan.getTotalInvested())
                .totalShares(plan.getTotalShares())
                .totalExecutions(plan.getTotalExecutions())
                .nextExecutionDate(plan.getNextExecutionDate())
                .lastExecutedAt(plan.getLastExecutedAt())
                .startedAt(plan.getStartedAt())
                .endedAt(plan.getEndedAt())
                .createdAt(plan.getCreatedAt())
                .build();
    }
}
