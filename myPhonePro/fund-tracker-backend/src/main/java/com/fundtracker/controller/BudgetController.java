package com.fundtracker.controller;

import com.fundtracker.model.dto.*;
import com.fundtracker.service.BudgetService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 年度预算账本：预算类型 + 记账流水。
 * 与「账户与现金」「分红覆盖度」互不联动（v1 口径）。
 */
@RestController
@RequestMapping("/api/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping("/overview")
    public ApiResponse<BudgetOverviewDTO> overview(@RequestParam(required = false) Integer year,
                                                   HttpServletRequest request) {
        return ApiResponse.success(budgetService.overview(userId(request), year));
    }

    @GetMapping("/categories")
    public ApiResponse<List<BudgetCategoryDTO>> listCategories(@RequestParam(required = false) Integer year,
                                                               HttpServletRequest request) {
        return ApiResponse.success(budgetService.listCategories(userId(request), year));
    }

    @PostMapping("/categories")
    public ApiResponse<BudgetCategoryDTO> createCategory(@RequestBody CreateBudgetCategoryReq req,
                                                         HttpServletRequest request) {
        return ApiResponse.success(budgetService.createCategory(userId(request), req));
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<BudgetCategoryDTO> updateCategory(@PathVariable String id,
                                                         @RequestBody UpdateBudgetCategoryReq req,
                                                         HttpServletRequest request) {
        return ApiResponse.success(budgetService.updateCategory(userId(request), id, req));
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable String id, HttpServletRequest request) {
        budgetService.deleteCategory(userId(request), id);
        return ApiResponse.success(null);
    }

    @GetMapping("/records")
    public ApiResponse<List<BudgetRecordDTO>> listRecords(@RequestParam(required = false) Integer year,
                                                          HttpServletRequest request) {
        return ApiResponse.success(budgetService.listRecords(userId(request), year));
    }

    @PostMapping("/records")
    public ApiResponse<BudgetRecordDTO> createRecord(@RequestBody CreateBudgetRecordReq req,
                                                     HttpServletRequest request) {
        return ApiResponse.success(budgetService.createRecord(userId(request), req));
    }

    @DeleteMapping("/records/{id}")
    public ApiResponse<Void> deleteRecord(@PathVariable String id, HttpServletRequest request) {
        budgetService.deleteRecord(userId(request), id);
        return ApiResponse.success(null);
    }

    private String userId(HttpServletRequest request) {
        return (String) request.getAttribute("userId");
    }
}
