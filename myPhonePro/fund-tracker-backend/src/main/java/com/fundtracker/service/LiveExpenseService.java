package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.*;
import com.fundtracker.model.entity.LiveExpense;
import com.fundtracker.repository.LiveExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LiveExpenseService {

    private final LiveExpenseRepository expenseRepository;
    private final DashboardService dashboardService;

    public List<LiveExpenseDTO> listAll() {
        return expenseRepository.findByDeletedFalseOrderBySortOrderAsc()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public LiveExpenseDTO create(CreateExpenseReq req) {
        int nextSort = (int) expenseRepository.countByDeletedFalse();
        LiveExpense expense = LiveExpense.builder()
                .id(UUID.randomUUID().toString())
                .name(req.getName())
                .icon(req.getIcon())
                .monthlyAmount(req.getMonthlyAmount())
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : nextSort)
                .build();
        expense = expenseRepository.save(expense);
        log.info("创建生活支出: {} - {}元/月", expense.getName(), expense.getMonthlyAmount());
        return toDTO(expense);
    }

    @Transactional
    public LiveExpenseDTO update(String id, UpdateExpenseReq req) {
        LiveExpense expense = expenseRepository.findById(id)
                .orElseThrow(() -> BusinessException.expenseNotFound(id));
        if (req.getName() != null) expense.setName(req.getName());
        if (req.getIcon() != null) expense.setIcon(req.getIcon());
        if (req.getMonthlyAmount() != null) expense.setMonthlyAmount(req.getMonthlyAmount());
        if (req.getSortOrder() != null) expense.setSortOrder(req.getSortOrder());
        expense = expenseRepository.save(expense);
        return toDTO(expense);
    }

    @Transactional
    public void delete(String id) {
        LiveExpense expense = expenseRepository.findById(id)
                .orElseThrow(() -> BusinessException.expenseNotFound(id));
        expense.setDeleted(true);
        expenseRepository.save(expense);
        log.info("删除生活支出: {}", expense.getName());
    }

    public CoverageDTO getCoverageSummary() {
        DashboardDTO dashboard = dashboardService.getDashboard();
        List<LiveExpense> expenses = expenseRepository.findByDeletedFalseOrderBySortOrderAsc();

        BigDecimal predictedAnnualDividend = dashboard.getPredictedAnnualDividend() != null
                ? dashboard.getPredictedAnnualDividend() : BigDecimal.ZERO;
        BigDecimal totalDividendReceived = dashboard.getTotalDividendReceived() != null
                ? dashboard.getTotalDividendReceived() : BigDecimal.ZERO;

        BigDecimal totalAnnualExpense = BigDecimal.ZERO;
        List<CoverageDTO.ExpenseCoverageItem> expenseItems = new ArrayList<>();

        int coveredCount = 0;
        BigDecimal accumulatedDividend = BigDecimal.ZERO;

        // Sort expenses by monthly amount ascending so smaller expenses get covered first
        expenses.sort(Comparator.comparing(LiveExpense::getMonthlyAmount));

        for (int i = 0; i < expenses.size(); i++) {
            LiveExpense exp = expenses.get(i);
            BigDecimal annualAmount = exp.getMonthlyAmount().multiply(BigDecimal.valueOf(12));
            totalAnnualExpense = totalAnnualExpense.add(annualAmount);

            boolean covered = false;
            boolean inProgress = false;

            // Check if this expense can be covered by predicted annual dividend
            BigDecimal neededForCoverage = accumulatedDividend.add(annualAmount);
            if (neededForCoverage.compareTo(predictedAnnualDividend) <= 0) {
                covered = true;
                coveredCount++;
                accumulatedDividend = accumulatedDividend.add(annualAmount);
            } else if (accumulatedDividend.compareTo(predictedAnnualDividend) < 0) {
                inProgress = true;
                accumulatedDividend = predictedAnnualDividend;
            }

            expenseItems.add(CoverageDTO.ExpenseCoverageItem.builder()
                    .id(exp.getId())
                    .name(exp.getName())
                    .icon(exp.getIcon())
                    .annualAmount(annualAmount)
                    .covered(covered)
                    .inProgress(inProgress && !covered)
                    .build());
        }

        BigDecimal remainingDividend = predictedAnnualDividend.subtract(accumulatedDividend);
        if (remainingDividend.compareTo(BigDecimal.ZERO) < 0) {
            remainingDividend = BigDecimal.ZERO;
        }

        // Calculate milestone
        MilestoneData milestone = calculateMilestone(coveredCount, expenses.size());

        return CoverageDTO.builder()
                .totalExpenses(expenses.size())
                .coveredExpenses(coveredCount)
                .totalAnnualExpense(totalAnnualExpense)
                .predictedAnnualDividend(predictedAnnualDividend)
                .totalDividendReceived(totalDividendReceived)
                .remainingDividend(remainingDividend)
                .expenses(expenseItems)
                .currentMilestone(milestone.current)
                .currentMilestoneIndex(milestone.index)
                .nextMilestoneName(milestone.nextName)
                .nextMilestoneRemaining(milestone.nextRemaining)
                .build();
    }

    private MilestoneData calculateMilestone(int coveredCount, int totalExpenses) {
        CoverageDTO.MilestoneInfo[] milestones = {
                CoverageDTO.MilestoneInfo.builder().name("初出茅庐").icon("potted_plant").requiredExpenses(1)
                        .description("再覆盖 " + (1 - coveredCount) + " 项即达成").build(),
                CoverageDTO.MilestoneInfo.builder().name("小有所成").icon("eco").requiredExpenses(2).build(),
                CoverageDTO.MilestoneInfo.builder().name("渐入佳境").icon("forest").requiredExpenses(3).build(),
                CoverageDTO.MilestoneInfo.builder().name("收益达人").icon("park").requiredExpenses(4).build(),
                CoverageDTO.MilestoneInfo.builder().name("财务自由").icon("workspace_premium").requiredExpenses(totalExpenses).build()
        };

        int currentIndex = 0;
        CoverageDTO.MilestoneInfo current = milestones[0];
        for (int i = milestones.length - 1; i >= 0; i--) {
            if (coveredCount >= milestones[i].getRequiredExpenses()) {
                current = milestones[i];
                currentIndex = i;
                break;
            }
        }

        // Find next milestone
        String nextName = "";
        BigDecimal nextRemaining = BigDecimal.ZERO;
        for (int i = currentIndex + 1; i < milestones.length; i++) {
            if (coveredCount < milestones[i].getRequiredExpenses()) {
                nextName = milestones[i].getName();
                break;
            }
        }

        return new MilestoneData(current, currentIndex, nextName, nextRemaining);
    }

    private LiveExpenseDTO toDTO(LiveExpense expense) {
        return LiveExpenseDTO.builder()
                .id(expense.getId())
                .name(expense.getName())
                .icon(expense.getIcon())
                .monthlyAmount(expense.getMonthlyAmount())
                .sortOrder(expense.getSortOrder())
                .build();
    }

    @lombok.Value
    private static class MilestoneData {
        CoverageDTO.MilestoneInfo current;
        int index;
        String nextName;
        BigDecimal nextRemaining;
    }
}
