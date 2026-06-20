package com.fundtracker.controller;

import com.fundtracker.model.dto.AnnualInsightResp;
import com.fundtracker.model.dto.ApiResponse;
import com.fundtracker.model.dto.MonthlyDetailResp;
import com.fundtracker.model.dto.MonthlyInsightResp;
import com.fundtracker.service.InsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;

    @GetMapping("/monthly")
    public ApiResponse<MonthlyInsightResp> getMonthlyInsight(
            @RequestParam int year, @RequestParam int month) {
        return ApiResponse.success(insightService.getMonthlyInsight(year, month));
    }

    @GetMapping("/monthly-detail")
    public ApiResponse<MonthlyDetailResp> getMonthlyDetail(
            @RequestParam int year, @RequestParam int month) {
        return ApiResponse.success(insightService.getMonthlyDetail(year, month));
    }

    @GetMapping("/annual")
    public ApiResponse<AnnualInsightResp> getAnnualInsight(@RequestParam int year) {
        return ApiResponse.success(insightService.getAnnualInsight(year));
    }
}
