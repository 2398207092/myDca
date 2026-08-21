package com.fundtracker.controller;

import com.fundtracker.model.dto.*;
import com.fundtracker.service.DividendInfoService;
import com.fundtracker.service.ForecastService;
import com.fundtracker.service.FundNavScrapeService;
import com.fundtracker.service.FundSearchService;
import com.fundtracker.service.HoldingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/holdings")
@RequiredArgsConstructor
public class HoldingController {

    private final HoldingService holdingService;
    private final ForecastService forecastService;
    private final FundSearchService fundSearchService;
    private final DividendInfoService dividendInfoService;
    private final FundNavScrapeService fundNavScrapeService;

    @GetMapping
    public ApiResponse<List<HoldingDTO>> listHoldings(
            HttpServletRequest request,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(holdingService.listHoldings(userId, type, keyword));
    }

    @GetMapping("/nav")
    public ApiResponse<FundNavScrapeService.LatestNavResult> getNavByDate(
            @RequestParam String code,
            @RequestParam(value = "date", required = false) String date) {
        // 仅读取本地 fund_nav_records：外部净值 API 只在每日定时任务（22:00）喂表
        LocalDate d = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();
        FundNavScrapeService.LatestNavResult result = fundNavScrapeService.getLatestNavBefore(code, d);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<HoldingDTO> getHolding(
            HttpServletRequest request,
            @PathVariable String id) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(holdingService.getHolding(id, userId));
    }

    @PostMapping
    public ApiResponse<HoldingDTO> createHolding(
            HttpServletRequest request,
            @Valid @RequestBody CreateHoldingReq req) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success("创建成功", holdingService.createHolding(userId, req));
    }

    @PutMapping("/{id}")
    public ApiResponse<HoldingDTO> updateHolding(
            HttpServletRequest request,
            @PathVariable String id,
            @RequestBody UpdateHoldingReq req) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success("更新成功", holdingService.updateHolding(id, req, userId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteResp> deleteHolding(
            HttpServletRequest request,
            @PathVariable String id) {
        String userId = (String) request.getAttribute("userId");
        holdingService.deleteHolding(userId, id);
        return ApiResponse.success(new DeleteResp(true));
    }

    @GetMapping("/{id}/forecast")
    public ApiResponse<ForecastResp> getForecast(
            HttpServletRequest request,
            @PathVariable String id,
            @RequestParam(defaultValue = "12m") String period) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(forecastService.getForecast(id, period, userId));
    }

    @GetMapping("/dividend-info")
    public ApiResponse<DividendInfoDTO> getDividendInfo(
            @RequestParam String code,
            @RequestParam(defaultValue = "基金") String type,
            @RequestParam(defaultValue = "ex_date") String method,
            @RequestParam(defaultValue = "3y") String horizon) {
        return ApiResponse.success(dividendInfoService.getDividendInfo(code, type, method, horizon));
    }

    @PutMapping("/{id}/category")
    public ApiResponse<HoldingDTO> updateHoldingCategory(
            HttpServletRequest request,
            @PathVariable String id,
            @Valid @RequestBody UpdateHoldingCategoryReq req) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success("分类更新成功", holdingService.updateHoldingCategory(userId, id, req));
    }

    @PutMapping("/{id}/dividend-reinvest")
    public ApiResponse<HoldingDTO> updateDividendReinvest(
            HttpServletRequest request,
            @PathVariable String id,
            @RequestBody UpdateDividendReinvestReq req) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success("复投设置更新成功", holdingService.updateDividendReinvest(userId, id, req));
    }

    @GetMapping("/search")
    public ApiResponse<List<HoldingSearchResult>> searchHoldings(
            @RequestParam String keyword) {
        return ApiResponse.success(fundSearchService.search(keyword));
    }
}
