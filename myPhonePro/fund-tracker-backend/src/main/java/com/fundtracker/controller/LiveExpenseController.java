package com.fundtracker.controller;

import com.fundtracker.model.dto.*;
import com.fundtracker.service.LiveExpenseService;
import jakarta.servlet.http.HttpServletRequest;
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
    public ApiResponse<List<LiveExpenseDTO>> listExpenses(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(expenseService.listAll(userId));
    }

    @PostMapping
    public ApiResponse<LiveExpenseDTO> createExpense(@Valid @RequestBody CreateExpenseReq req,
                                                      HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success("创建成功", expenseService.create(req, userId));
    }

    @PutMapping("/{id}")
    public ApiResponse<LiveExpenseDTO> updateExpense(
            @PathVariable String id,
            @Valid @RequestBody UpdateExpenseReq req,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success("更新成功", expenseService.update(id, req, userId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteExpense(@PathVariable String id,
                                           HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        expenseService.delete(id, userId);
        return ApiResponse.success("删除成功", null);
    }

    @GetMapping("/coverage")
    public ApiResponse<CoverageDTO> getCoverage(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(expenseService.getCoverageSummary(userId));
    }
}
