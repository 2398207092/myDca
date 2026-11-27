package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.*;
import com.fundtracker.model.entity.BudgetCategory;
import com.fundtracker.model.entity.BudgetRecord;
import com.fundtracker.repository.BudgetCategoryRepository;
import com.fundtracker.repository.BudgetRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 年度预算账本。
 * 口径：只统计 budget_records 里的开销，不扣现金账户、不参与分红覆盖度计算。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999.99");
    private static final int MAX_NAME_LENGTH = 30;
    private static final int MAX_NOTE_LENGTH = 200;
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    private final BudgetCategoryRepository categoryRepository;
    private final BudgetRecordRepository recordRepository;

    /** 年度总览：额度 / 已用 / 剩余 / 每个类型的明细 */
    public BudgetOverviewDTO overview(String userId, Integer year) {
        int y = normalizeYear(year);
        List<BudgetCategory> cats = listCategoriesOf(userId, y);
        Map<String, List<BudgetRecord>> grouped = groupRecords(userId, cats);

        List<BudgetCategoryDTO> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalSpent = BigDecimal.ZERO;
        for (BudgetCategory cat : cats) {
            BudgetCategoryDTO dto = toDTO(cat, grouped.getOrDefault(cat.getId(), List.of()));
            items.add(dto);
            totalAmount = totalAmount.add(dto.getAmount());
            totalSpent = totalSpent.add(dto.getSpent());
        }
        BigDecimal remaining = totalAmount.subtract(totalSpent);
        BigDecimal usagePercent = totalAmount.compareTo(BigDecimal.ZERO) > 0
                ? totalSpent.multiply(BigDecimal.valueOf(100)).divide(totalAmount, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return BudgetOverviewDTO.builder()
                .year(y)
                .totalAmount(money(totalAmount))
                .totalSpent(money(totalSpent))
                .remaining(money(remaining))
                .usagePercent(money(usagePercent))
                .categories(items)
                .build();
    }

    public List<BudgetCategoryDTO> listCategories(String userId, Integer year) {
        int y = normalizeYear(year);
        List<BudgetCategory> cats = listCategoriesOf(userId, y);
        Map<String, List<BudgetRecord>> grouped = groupRecords(userId, cats);
        return cats.stream()
                .map(c -> toDTO(c, grouped.getOrDefault(c.getId(), List.of())))
                .collect(Collectors.toList());
    }

    @Transactional
    public BudgetCategoryDTO createCategory(String userId, CreateBudgetCategoryReq req) {
        int year = normalizeYear(req.getYear());
        String name = requireName(req.getName());
        BigDecimal amount = requireAmount(req.getAmount());

        List<BudgetCategory> existing = listCategoriesOf(userId, year);
        if (existing.stream().anyMatch(c -> c.getName().equals(name))) {
            throw BusinessException.invalidParam("已存在同名预算类型「" + name + "」");
        }
        int sort = req.getSortOrder() != null ? req.getSortOrder() : existing.size();

        BudgetCategory saved = categoryRepository.save(BudgetCategory.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .year(year)
                .name(name)
                .amount(amount)
                .sortOrder(sort)
                .build());
        log.info("创建预算类型: {} {}年 额度 {}", name, year, amount);
        return toDTO(saved, List.of());
    }

    @Transactional
    public BudgetCategoryDTO updateCategory(String userId, String id, UpdateBudgetCategoryReq req) {
        BudgetCategory cat = requireCategory(userId, id);
        if (req.getName() != null) {
            cat.setName(requireName(req.getName()));
        }
        if (req.getAmount() != null) {
            cat.setAmount(requireAmount(req.getAmount()));
        }
        if (req.getSortOrder() != null) {
            cat.setSortOrder(req.getSortOrder());
        }
        BudgetCategory saved = categoryRepository.save(cat);
        List<BudgetRecord> records = recordRepository
                .findByUserIdAndCategoryIdInAndDeletedFalseOrderByOccurDateDescIdDesc(userId, List.of(saved.getId()));
        return toDTO(saved, records);
    }

    @Transactional
    public void deleteCategory(String userId, String id) {
        BudgetCategory cat = requireCategory(userId, id);
        long recordCount = recordRepository.countByCategoryIdAndDeletedFalse(cat.getId());
        if (recordCount > 0) {
            throw BusinessException.invalidParam("该类型下还有 " + recordCount + " 笔记录，请先删除记录");
        }
        cat.setDeleted(true);
        categoryRepository.save(cat);
        log.info("删除预算类型: {} {}", cat.getYear(), cat.getName());
    }

    /** 某年度全部记账流水（按日期倒序） */
    public List<BudgetRecordDTO> listRecords(String userId, Integer year) {
        int y = normalizeYear(year);
        List<BudgetCategory> cats = listCategoriesOf(userId, y);
        if (cats.isEmpty()) {
            return List.of();
        }
        Map<String, String> nameById = cats.stream()
                .collect(Collectors.toMap(BudgetCategory::getId, BudgetCategory::getName));
        return recordRepository
                .findByUserIdAndCategoryIdInAndDeletedFalseOrderByOccurDateDescIdDesc(userId, nameById.keySet())
                .stream()
                .map(r -> toRecordDTO(r, nameById.get(r.getCategoryId())))
                .collect(Collectors.toList());
    }

    @Transactional
    public BudgetRecordDTO createRecord(String userId, CreateBudgetRecordReq req) {
        if (req.getCategoryId() == null || req.getCategoryId().isBlank()) {
            throw BusinessException.invalidParam("请选择预算类型");
        }
        BudgetCategory cat = requireCategory(userId, req.getCategoryId());
        BigDecimal amount = requireAmount(req.getAmount());
        LocalDate date = req.getOccurDate() != null ? req.getOccurDate() : LocalDate.now();
        if (date.getYear() != cat.getYear()) {
            throw BusinessException.invalidParam("记账日期必须落在 " + cat.getYear() + " 年内");
        }
        BudgetRecord saved = recordRepository.save(BudgetRecord.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .categoryId(cat.getId())
                .occurDate(date)
                .amount(amount)
                .note(requireNote(req.getNote()))
                .build());
        log.info("预算记账: {} {} 元 ({})", cat.getName(), amount, date);
        return toRecordDTO(saved, cat.getName());
    }

    @Transactional
    public void deleteRecord(String userId, String id) {
        BudgetRecord rec = recordRepository.findByIdAndUserIdAndDeletedFalse(id, userId)
                .orElseThrow(BusinessException::budgetRecordNotFound);
        rec.setDeleted(true);
        recordRepository.save(rec);
        log.info("删除预算记录: {} {} 元", rec.getCategoryId(), rec.getAmount());
    }

    // ==================== 内部 ====================

    private List<BudgetCategory> listCategoriesOf(String userId, int year) {
        return categoryRepository.findByUserIdAndYearAndDeletedFalseOrderBySortOrderAscIdAsc(userId, year);
    }

    private Map<String, List<BudgetRecord>> groupRecords(String userId, List<BudgetCategory> cats) {
        if (cats.isEmpty()) {
            return Map.of();
        }
        List<String> ids = cats.stream().map(BudgetCategory::getId).collect(Collectors.toList());
        return recordRepository.findByUserIdAndCategoryIdInAndDeletedFalseOrderByOccurDateDescIdDesc(userId, ids)
                .stream()
                .collect(Collectors.groupingBy(BudgetRecord::getCategoryId));
    }

    private BudgetCategory requireCategory(String userId, String id) {
        return categoryRepository.findByIdAndUserIdAndDeletedFalse(id, userId)
                .orElseThrow(BusinessException::budgetCategoryNotFound);
    }

    private BudgetCategoryDTO toDTO(BudgetCategory cat, List<BudgetRecord> records) {
        BigDecimal spent = records.stream()
                .map(BudgetRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal budget = money(cat.getAmount());
        return BudgetCategoryDTO.builder()
                .id(cat.getId())
                .name(cat.getName())
                .amount(budget)
                .spent(money(spent))
                .remaining(money(budget.subtract(spent)))
                .recordCount(records.size())
                .sortOrder(cat.getSortOrder())
                .build();
    }

    private BudgetRecordDTO toRecordDTO(BudgetRecord record, String categoryName) {
        return BudgetRecordDTO.builder()
                .id(record.getId())
                .categoryId(record.getCategoryId())
                .categoryName(categoryName)
                .occurDate(record.getOccurDate() == null ? null : record.getOccurDate().toString())
                .amount(money(record.getAmount()))
                .note(record.getNote())
                .build();
    }

    private static int normalizeYear(Integer year) {
        int y = year != null ? year : LocalDate.now().getYear();
        if (y < MIN_YEAR || y > MAX_YEAR) {
            throw BusinessException.invalidParam("预算年度不合法: " + y);
        }
        return y;
    }

    private static String requireName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw BusinessException.invalidParam("请填写预算类型名称");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw BusinessException.invalidParam("预算类型名称最多 " + MAX_NAME_LENGTH + " 个字");
        }
        return name;
    }

    private static String requireNote(String raw) {
        String note = raw == null ? "" : raw.trim();
        return note.length() > MAX_NOTE_LENGTH ? note.substring(0, MAX_NOTE_LENGTH) : note;
    }

    private static BigDecimal requireAmount(BigDecimal raw) {
        if (raw == null) {
            throw BusinessException.invalidParam("请填写金额");
        }
        BigDecimal amount = raw.setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessException.invalidParam("金额必须大于 0");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw BusinessException.invalidParam("金额超出上限（最大 " + MAX_AMOUNT + "）");
        }
        return amount;
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
