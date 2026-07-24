package com.fundtracker.controller;

import com.fundtracker.model.dto.*;
import com.fundtracker.service.DcaPlanService;
import jakarta.servlet.http.HttpServletRequest;
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
    public ApiResponse<DcaPlanVO> createPlan(
            @Valid @RequestBody CreateDcaPlanReq req,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success("创建定投计划成功", dcaPlanService.createPlan(req, userId));
    }

    @GetMapping
    public ApiResponse<List<DcaPlanVO>> listPlans(
            @RequestParam(required = false) String holdingId,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(dcaPlanService.listPlans(holdingId, userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<DcaPlanVO> getPlan(
            @PathVariable String id,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(dcaPlanService.getPlan(id, userId));
    }

    @PutMapping("/{id}")
    public ApiResponse<DcaPlanVO> updatePlan(
            @PathVariable String id,
            @Valid @RequestBody UpdateDcaPlanReq req,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success("更新定投计划成功", dcaPlanService.updatePlan(id, req, userId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteResp> deletePlan(
            @PathVariable String id,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        dcaPlanService.deletePlan(id, userId);
        return ApiResponse.success(new DeleteResp(true));
    }

    @PostMapping("/{id}/execute")
    public ApiResponse<DcaExecutionResultVO> executePlan(
            @PathVariable String id,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success("定投执行成功", dcaPlanService.executePlan(id, userId));
    }

    @GetMapping("/budget")
    public ApiResponse<DcaBudgetVO> getBudget(
            @RequestParam int year,
            @RequestParam int month,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(dcaPlanService.calculateBudget(year, month, userId));
    }
}
