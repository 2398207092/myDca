package com.fundtracker.controller;

import com.fundtracker.model.dto.AnnualInsightResp;
import com.fundtracker.model.dto.ApiResponse;
import com.fundtracker.model.dto.MonthlyDetailResp;
import com.fundtracker.model.dto.MonthlyInsightResp;
import com.fundtracker.service.InsightService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;

    @GetMapping("/monthly")
    public ApiResponse<MonthlyInsightResp> getMonthlyInsight(
            @RequestParam int year, @RequestParam int month,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(insightService.getMonthlyInsight(year, month, userId));
    }

    @GetMapping("/monthly-detail")
    public ApiResponse<MonthlyDetailResp> getMonthlyDetail(
            @RequestParam int year, @RequestParam int month,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(insightService.getMonthlyDetail(year, month, userId));
    }

    @GetMapping("/annual")
    public ApiResponse<AnnualInsightResp> getAnnualInsight(
            @RequestParam int year,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(insightService.getAnnualInsight(year, userId));
    }
}
