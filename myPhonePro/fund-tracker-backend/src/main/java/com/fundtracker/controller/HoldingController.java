package com.fundtracker.controller;

import com.fundtracker.model.dto.*;
import com.fundtracker.service.DividendInfoService;
import com.fundtracker.service.ForecastService;
import com.fundtracker.service.FundSearchService;
import com.fundtracker.service.HoldingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/holdings")
@RequiredArgsConstructor
public class HoldingController {

    private final HoldingService holdingService;
    private final ForecastService forecastService;
    private final FundSearchService fundSearchService;
    private final DividendInfoService dividendInfoService;

    @GetMapping
    public ApiResponse<List<HoldingDTO>> listHoldings(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(holdingService.listHoldings(type, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<HoldingDTO> getHolding(@PathVariable String id) {
        return ApiResponse.success(holdingService.getHolding(id));
    }

    @PostMapping
    public ApiResponse<HoldingDTO> createHolding(@Valid @RequestBody CreateHoldingReq req) {
        return ApiResponse.success("创建成功", holdingService.createHolding(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<HoldingDTO> updateHolding(@PathVariable String id,
                                                  @RequestBody UpdateHoldingReq req) {
        return ApiResponse.success("更新成功", holdingService.updateHolding(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteResp> deleteHolding(@PathVariable String id) {
        holdingService.deleteHolding(id);
        return ApiResponse.success(new DeleteResp(true));
    }

    @GetMapping("/{id}/forecast")
    public ApiResponse<ForecastResp> getForecast(
            @PathVariable String id,
            @RequestParam(defaultValue = "12m") String period) {
        return ApiResponse.success(forecastService.getForecast(id, period));
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
    public ApiResponse<HoldingDTO> updateHoldingCategory(@PathVariable String id,
                                                          @Valid @RequestBody UpdateHoldingCategoryReq req) {
        return ApiResponse.success("分类更新成功", holdingService.updateHoldingCategory(id, req));
    }

    @GetMapping("/search")
    public ApiResponse<List<HoldingSearchResult>> searchHoldings(
            @RequestParam String keyword) {
        return ApiResponse.success(fundSearchService.search(keyword));
    }
}
