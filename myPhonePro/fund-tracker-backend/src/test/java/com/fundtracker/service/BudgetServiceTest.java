package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.*;
import com.fundtracker.model.entity.BudgetCategory;
import com.fundtracker.model.entity.BudgetRecord;
import com.fundtracker.repository.BudgetCategoryRepository;
import com.fundtracker.repository.BudgetRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 预算账本的钱口径（额度 / 已用 / 剩余 / 超额）——改这里必须先改测试。
 */
@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    private static final String USER = "u-1";
    private static final int YEAR = 2026;

    @Mock private BudgetCategoryRepository categoryRepository;
    @Mock private BudgetRecordRepository recordRepository;

    private BudgetService service;

    @BeforeEach
    void setUp() {
        service = new BudgetService(categoryRepository, recordRepository);
    }

    private BudgetCategory cat(String id, String name, String amount) {
        return BudgetCategory.builder()
                .id(id).userId(USER).year(YEAR).name(name)
                .amount(new BigDecimal(amount)).sortOrder(0).build();
    }

    private BudgetRecord rec(String categoryId, String amount) {
        return BudgetRecord.builder()
                .id("r-" + categoryId + "-" + amount)
                .userId(USER).categoryId(categoryId)
                .occurDate(LocalDate.of(YEAR, 11, 20))
                .amount(new BigDecimal(amount))
                .build();
    }

    @Test
    @DisplayName("总览：额度合计 / 已用合计 / 剩余按类型累加，百分比保留两位")
    void overview_sums() {
        List<BudgetCategory> cats = List.of(cat("c1", "餐饮", "3000.00"), cat("c2", "日用", "2000.00"));
        when(categoryRepository.findByUserIdAndYearAndDeletedFalseOrderBySortOrderAscIdAsc(USER, YEAR)).thenReturn(cats);
        when(recordRepository.findByUserIdAndCategoryIdInAndDeletedFalseOrderByOccurDateDescIdDesc(eq(USER), anyCollection()))
                .thenReturn(List.of(rec("c1", "1200.00"), rec("c1", "300.00"), rec("c2", "200.00")));

        BudgetOverviewDTO dto = service.overview(USER, YEAR);

        assertEquals(new BigDecimal("5000.00"), dto.getTotalAmount());
        assertEquals(new BigDecimal("1700.00"), dto.getTotalSpent());
        assertEquals(new BigDecimal("3300.00"), dto.getRemaining());
        // 1700 / 5000 = 34.00%
        assertEquals(new BigDecimal("34.00"), dto.getUsagePercent());
        assertEquals(2, dto.getCategories().size());
        assertEquals(new BigDecimal("1500.00"), dto.getCategories().get(0).getRemaining());
        assertEquals(2, dto.getCategories().get(0).getRecordCount());
        assertEquals(new BigDecimal("1800.00"), dto.getCategories().get(1).getRemaining());
    }

    @Test
    @DisplayName("超支：剩余为负数，百分比可超过 100")
    void overview_overspent() {
        when(categoryRepository.findByUserIdAndYearAndDeletedFalseOrderBySortOrderAscIdAsc(USER, YEAR))
                .thenReturn(List.of(cat("c1", "餐饮", "1000.00")));
        when(recordRepository.findByUserIdAndCategoryIdInAndDeletedFalseOrderByOccurDateDescIdDesc(eq(USER), anyCollection()))
                .thenReturn(List.of(rec("c1", "1250.50")));

        BudgetOverviewDTO dto = service.overview(USER, YEAR);

        assertEquals(new BigDecimal("-250.50"), dto.getRemaining());
        assertEquals(new BigDecimal("-250.50"), dto.getCategories().get(0).getRemaining());
        assertEquals(new BigDecimal("125.05"), dto.getUsagePercent());
    }

    @Test
    @DisplayName("还没有预算类型：全零，不查流水，不除零")
    void overview_empty() {
        when(categoryRepository.findByUserIdAndYearAndDeletedFalseOrderBySortOrderAscIdAsc(USER, YEAR))
                .thenReturn(List.of());

        BudgetOverviewDTO dto = service.overview(USER, YEAR);

        assertEquals(new BigDecimal("0.00"), dto.getTotalAmount());
        assertEquals(new BigDecimal("0.00"), dto.getRemaining());
        assertEquals(new BigDecimal("0.00"), dto.getUsagePercent());
        assertTrue(dto.getCategories().isEmpty());
        verifyNoInteractions(recordRepository);
    }

    @Test
    @DisplayName("新建预算类型：金额四舍五入到两位，同年度重名被拒")
    void createCategory_rules() {
        when(categoryRepository.findByUserIdAndYearAndDeletedFalseOrderBySortOrderAscIdAsc(USER, YEAR))
                .thenReturn(List.of());
        when(categoryRepository.save(any(BudgetCategory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateBudgetCategoryReq req = new CreateBudgetCategoryReq();
        req.setYear(YEAR);
        req.setName("  餐饮  ");
        req.setAmount(new BigDecimal("1200.345"));

        BudgetCategoryDTO dto = service.createCategory(USER, req);

        assertEquals("餐饮", dto.getName());
        assertEquals(new BigDecimal("1200.35"), dto.getAmount());
        assertEquals(new BigDecimal("0.00"), dto.getSpent());

        when(categoryRepository.findByUserIdAndYearAndDeletedFalseOrderBySortOrderAscIdAsc(USER, YEAR))
                .thenReturn(List.of(cat("c1", "餐饮", "1000.00")));
        CreateBudgetCategoryReq dup = new CreateBudgetCategoryReq();
        dup.setYear(YEAR);
        dup.setName("餐饮");
        dup.setAmount(new BigDecimal("100"));
        BusinessException e = assertThrows(BusinessException.class, () -> service.createCategory(USER, dup));
        assertTrue(e.getMessage().contains("已存在"));
    }

    @Test
    @DisplayName("金额必须大于 0，空金额被拒")
    void amount_validation() {
        CreateBudgetCategoryReq zero = new CreateBudgetCategoryReq();
        zero.setYear(YEAR);
        zero.setName("餐饮");
        zero.setAmount(BigDecimal.ZERO);
        assertThrows(BusinessException.class, () -> service.createCategory(USER, zero));

        CreateBudgetCategoryReq nullAmount = new CreateBudgetCategoryReq();
        nullAmount.setYear(YEAR);
        nullAmount.setName("餐饮");
        assertThrows(BusinessException.class, () -> service.createCategory(USER, nullAmount));
    }

    @Test
    @DisplayName("记账：日期必须落在预算年度内，金额四舍五入")
    void createRecord_rules() {
        when(categoryRepository.findByIdAndUserIdAndDeletedFalse("c1", USER))
                .thenReturn(Optional.of(cat("c1", "餐饮", "3000.00")));
        when(recordRepository.save(any(BudgetRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateBudgetRecordReq ok = new CreateBudgetRecordReq();
        ok.setCategoryId("c1");
        ok.setOccurDate(LocalDate.of(YEAR, 3, 5));
        ok.setAmount(new BigDecimal("99.999"));
        ok.setNote("  午饭  ");

        BudgetRecordDTO dto = service.createRecord(USER, ok);
        assertEquals(new BigDecimal("100.00"), dto.getAmount());
        assertEquals("2026-03-05", dto.getOccurDate());
        assertEquals("午饭", dto.getNote());
        assertEquals("餐饮", dto.getCategoryName());

        CreateBudgetRecordReq crossYear = new CreateBudgetRecordReq();
        crossYear.setCategoryId("c1");
        crossYear.setOccurDate(LocalDate.of(YEAR + 1, 1, 2));
        crossYear.setAmount(new BigDecimal("10"));
        BusinessException e = assertThrows(BusinessException.class, () -> service.createRecord(USER, crossYear));
        assertTrue(e.getMessage().contains("年内"));
    }

    @Test
    @DisplayName("删除预算类型：还有流水时拒绝删除")
    void deleteCategory_blocked_by_records() {
        when(categoryRepository.findByIdAndUserIdAndDeletedFalse("c1", USER))
                .thenReturn(Optional.of(cat("c1", "餐饮", "3000.00")));
        when(recordRepository.countByCategoryIdAndDeletedFalse("c1")).thenReturn(3L);

        BusinessException e = assertThrows(BusinessException.class, () -> service.deleteCategory(USER, "c1"));
        assertTrue(e.getMessage().contains("3 笔记录"));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("删除预算类型：无流水时软删")
    void deleteCategory_soft_delete() {
        BudgetCategory c = cat("c1", "餐饮", "3000.00");
        when(categoryRepository.findByIdAndUserIdAndDeletedFalse("c1", USER)).thenReturn(Optional.of(c));
        when(recordRepository.countByCategoryIdAndDeletedFalse("c1")).thenReturn(0L);

        service.deleteCategory(USER, "c1");

        ArgumentCaptor<BudgetCategory> captor = ArgumentCaptor.forClass(BudgetCategory.class);
        verify(categoryRepository).save(captor.capture());
        assertTrue(captor.getValue().isDeleted());
    }

    @Test
    @DisplayName("越权：别人的预算类型查不到")
    void access_control() {
        when(categoryRepository.findByIdAndUserIdAndDeletedFalse("c-other", USER)).thenReturn(Optional.empty());

        CreateBudgetRecordReq req = new CreateBudgetRecordReq();
        req.setCategoryId("c-other");
        req.setAmount(new BigDecimal("10"));
        assertThrows(BusinessException.class, () -> service.createRecord(USER, req));
    }

    @Test
    @DisplayName("流水列表带类型名，按年度过滤")
    void listRecords_with_category_name() {
        when(categoryRepository.findByUserIdAndYearAndDeletedFalseOrderBySortOrderAscIdAsc(USER, YEAR))
                .thenReturn(List.of(cat("c1", "餐饮", "3000.00")));
        when(recordRepository.findByUserIdAndCategoryIdInAndDeletedFalseOrderByOccurDateDescIdDesc(eq(USER), anyCollection()))
                .thenReturn(List.of(rec("c1", "42.00")));

        List<BudgetRecordDTO> list = service.listRecords(USER, YEAR);

        assertEquals(1, list.size());
        assertEquals("餐饮", list.get(0).getCategoryName());
        assertEquals(new BigDecimal("42.00"), list.get(0).getAmount());
    }
}
