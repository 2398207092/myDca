package com.fundtracker.controller;

import com.fundtracker.model.dto.*;
import com.fundtracker.service.DcaPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dca-plans")
@RequiredArgsConstructor
public class DcaPlanController {

    private final DcaPlanService dcaPlanService;

    @PostMapping
    public ApiResponse<DcaPlanVO> createPlan(@Valid @RequestBody CreateDcaPlanReq req) {
        return ApiResponse.success("创建定投计划成功", dcaPlanService.createPlan(req));
    }

    @GetMapping
    public ApiResponse<List<DcaPlanVO>> listPlans(
            @RequestParam(required = false) String holdingId) {
        return ApiResponse.success(dcaPlanService.listPlans(holdingId));
    }

    @GetMapping("/{id}")
    public ApiResponse<DcaPlanVO> getPlan(@PathVariable String id) {
        return ApiResponse.success(dcaPlanService.getPlan(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<DcaPlanVO> updatePlan(
            @PathVariable String id,
            @Valid @RequestBody UpdateDcaPlanReq req) {
        return ApiResponse.success("更新定投计划成功", dcaPlanService.updatePlan(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteResp> deletePlan(@PathVariable String id) {
        dcaPlanService.deletePlan(id);
        return ApiResponse.success(new DeleteResp(true));
    }

    @PostMapping("/{id}/execute")
    public ApiResponse<DcaExecutionResultVO> executePlan(@PathVariable String id) {
        return ApiResponse.success("定投执行成功", dcaPlanService.executePlan(id));
    }

    @GetMapping("/budget")
    public ApiResponse<DcaBudgetVO> getBudget(
            @RequestParam int year,
            @RequestParam int month) {
        return ApiResponse.success(dcaPlanService.calculateBudget(year, month));
    }
}
