package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.*;
import com.fundtracker.model.entity.DcaPlan;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.enums.DcaFrequency;
import com.fundtracker.model.enums.HoldingType;
import com.fundtracker.repository.DcaPlanRepository;
import com.fundtracker.repository.HoldingRepository;
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

    /**
     * 根据持仓类型推断交易市场
     */
    public static String inferTradingMarket(Holding holding) {
        HoldingType type = holding.getType();
        switch (type) {
            case 美股:
                return "us";
            default:
                return "china";
        }
    }

    /**
     * 计算指定月份的定投预算
     */
    public DcaBudgetVO calculateBudget(int year, int month) {
        List<DcaPlan> activePlans = dcaPlanRepository.findAll().stream()
                .filter(p -> "active".equals(p.getStatus()))
                .collect(Collectors.toList());

        if (activePlans.isEmpty()) {
            return DcaBudgetVO.builder()
                    .month(year + "-" + String.format("%02d", month))
                    .tradingDays(0)
                    .totalAmount(BigDecimal.ZERO)
                    .plans(List.of())
                    .build();
        }

        // 用第一个活跃计划的 market 来算交易日数（统一用 china）
        int tradingDays = tradingCalendar.countTradingDaysInMonth(year, month, "china");
        BigDecimal total = BigDecimal.ZERO;
        List<DcaBudgetVO.PlanBudgetItem> items = new java.util.ArrayList<>();

        for (DcaPlan plan : activePlans) {
            Holding holding = holdingRepository.findByIdAndDeletedFalse(plan.getHoldingId()).orElse(null);
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
                .tradingDays(tradingDays)
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

    @Transactional
    public DcaPlanVO createPlan(CreateDcaPlanReq req) {
        Holding holding = holdingRepository.findByIdAndDeletedFalse(req.getHoldingId())
                .orElseThrow(BusinessException::holdingNotFound);

        DcaFrequency frequency;
        try {
            frequency = DcaFrequency.valueOf(req.getFrequency());
        } catch (IllegalArgumentException e) {
            throw BusinessException.invalidParam("定投频率无效: " + req.getFrequency());
        }

        String market = inferTradingMarket(holding);
        LocalDate today = LocalDate.now();
        // 首期执行日：明天开始的第一个交易日
        LocalDate nextExec = tradingCalendar.nextTradingDay(today.plusDays(1), market);

        DcaPlan plan = DcaPlan.builder()
                .id(UUID.randomUUID().toString())
                .holdingId(req.getHoldingId())
                .amount(req.getAmount())
                .frequency(frequency)
                .day(req.getDay())
                .tradingMarket(market)
                .status("active")
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

    public List<DcaPlanVO> listPlans(String holdingId) {
        List<DcaPlan> plans;
        if (holdingId != null && !holdingId.isEmpty()) {
            plans = dcaPlanRepository.findByHoldingIdOrderByCreatedAtDesc(holdingId);
        } else {
            plans = dcaPlanRepository.findAll();
        }
        return plans.stream()
                .map(plan -> {
                    Holding h = holdingRepository.findByIdAndDeletedFalse(plan.getHoldingId()).orElse(null);
                    return toVO(plan, h);
                })
                .collect(Collectors.toList());
    }

    public DcaPlanVO getPlan(String id) {
        DcaPlan plan = dcaPlanRepository.findById(id)
                .orElseThrow(BusinessException::planNotFound);
        Holding h = holdingRepository.findByIdAndDeletedFalse(plan.getHoldingId()).orElse(null);
        return toVO(plan, h);
    }

    @Transactional
    public DcaPlanVO updatePlan(String id, UpdateDcaPlanReq req) {
        DcaPlan plan = dcaPlanRepository.findById(id)
                .orElseThrow(BusinessException::planNotFound);

        if (req.getAmount() != null) {
            plan.setAmount(req.getAmount());
        }
        if (req.getFrequency() != null) {
            plan.setFrequency(DcaFrequency.valueOf(req.getFrequency()));
        }
        if (req.getDay() != null) {
            plan.setDay(req.getDay());
        }
        if (req.getStatus() != null) {
            String newStatus = req.getStatus();
            switch (newStatus) {
                case "paused":
                    plan.setStatus("paused");
                    log.info("定投计划 {} 已暂停", id);
                    break;
                case "active":
                    plan.setStatus("active");
                    // 恢复时重置下次执行日为明天起的第一个交易日
                    plan.setNextExecutionDate(
                            tradingCalendar.nextTradingDay(LocalDate.now().plusDays(1), plan.getTradingMarket())
                    );
                    log.info("定投计划 {} 已恢复", id);
                    break;
                case "ended":
                    plan.setStatus("ended");
                    plan.setEndedAt(LocalDate.now());
                    log.info("定投计划 {} 已终止", id);
                    break;
                default:
                    throw BusinessException.invalidParam("无效的状态: " + newStatus);
            }
        }

        plan = dcaPlanRepository.save(plan);
        Holding h = holdingRepository.findByIdAndDeletedFalse(plan.getHoldingId()).orElse(null);
        return toVO(plan, h);
    }

    @Transactional
    public void deletePlan(String id) {
        DcaPlan plan = dcaPlanRepository.findById(id)
                .orElseThrow(BusinessException::planNotFound);
        // 物理删除计划，关联的 dca 交易保留
        dcaPlanRepository.delete(plan);
        log.info("删除定投计划 {} (关联交易已保留)", id);
    }

    /**
     * 执行一期定投
     */
    @Transactional
    public DcaExecutionResultVO executePlan(String id) {
        DcaPlan plan = dcaPlanRepository.findById(id)
                .orElseThrow(BusinessException::planNotFound);

        if (!"active".equals(plan.getStatus())) {
            throw BusinessException.invalidParam("定投计划已暂停或终止，无法执行");
        }

        Holding holding = holdingRepository.findByIdAndDeletedFalse(plan.getHoldingId())
                .orElseThrow(BusinessException::holdingNotFound);

        LocalDate today = LocalDate.now();
        String fundCode = holding.getCode();

        // 获取最新可用净值
        FundNavScrapeService.LatestNavResult nav = fundNavScrapeService.getLatestNavBefore(fundCode, today);
        if (nav == null || nav.unitNav() == null || nav.unitNav().compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessException.invalidParam("无法获取基金 " + fundCode + " 的净值数据，请确认净值已更新");
        }

        // 计算份额 = 金额 / 净值
        BigDecimal quantity = plan.getAmount()
                .divide(nav.unitNav(), 4, RoundingMode.HALF_UP);

        // 使用 TransactionService 创建买入交易
        CreateTransactionReq txReq = new CreateTransactionReq();
        txReq.setHoldingId(plan.getHoldingId());
        txReq.setType("buy");
        txReq.setDate(today.toString());
        txReq.setQuantity(quantity);
        txReq.setPrice(nav.unitNav());
        txReq.setFee(BigDecimal.ZERO);
        txReq.setSource("dca");
        txReq.setDcaPlanId(id);

        TransactionDTO txDto = transactionService.createTransaction(txReq);

        // 更新计划统计
        plan.setTotalInvested(plan.getTotalInvested().add(plan.getAmount()));
        plan.setTotalShares(plan.getTotalShares().add(quantity));
        plan.setTotalExecutions(plan.getTotalExecutions() + 1);
        plan.setLastExecutedAt(LocalDateTime.now());

        // 推进下次执行日期
        LocalDate nextExec = calculateNextExecutionDate(today, plan.getFrequency(), plan.getDay(), plan.getTradingMarket());
        plan.setNextExecutionDate(nextExec);
        dcaPlanRepository.save(plan);

        log.info("定投执行成功: plan={}, 金额={}, 份额={}, 净值={}, 下次执行={}",
                id, plan.getAmount(), quantity, nav.unitNav(), nextExec);

        return DcaExecutionResultVO.builder()
                .transactionId(txDto.getId())
                .amount(plan.getAmount())
                .quantity(quantity)
                .navPrice(nav.unitNav())
                .navDate(nav.navDate())
                .executionDate(today)
                .holdingName(holding.getName())
                .holdingCode(holding.getCode())
                .build();
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
                .status(plan.getStatus())
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
