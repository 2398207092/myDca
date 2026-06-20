package com.fundtracker.controller;

import com.fundtracker.model.dto.*;
import com.fundtracker.service.LiveExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class LiveExpenseController {

    private final LiveExpenseService expenseService;

    @GetMapping
    public ApiResponse<List<LiveExpenseDTO>> listExpenses() {
        return ApiResponse.success(expenseService.listAll());
    }

    @PostMapping
    public ApiResponse<LiveExpenseDTO> createExpense(@Valid @RequestBody CreateExpenseReq req) {
        return ApiResponse.success("创建成功", expenseService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<LiveExpenseDTO> updateExpense(
            @PathVariable String id,
            @Valid @RequestBody UpdateExpenseReq req) {
        return ApiResponse.success("更新成功", expenseService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteExpense(@PathVariable String id) {
        expenseService.delete(id);
        return ApiResponse.success("删除成功", null);
    }

    @GetMapping("/coverage")
    public ApiResponse<CoverageDTO> getCoverage() {
        return ApiResponse.success(expenseService.getCoverageSummary());
    }
}
