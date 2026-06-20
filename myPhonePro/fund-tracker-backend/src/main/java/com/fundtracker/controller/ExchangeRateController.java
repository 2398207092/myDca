package com.fundtracker.controller;

import com.fundtracker.model.dto.*;
import com.fundtracker.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @GetMapping
    public ApiResponse<List<ExchangeRateDTO>> listAll() {
        return ApiResponse.success(exchangeRateService.listAll());
    }

    @PostMapping("/refresh")
    public ApiResponse<RefreshExchangeRatesResp> refresh() {
        return ApiResponse.success(exchangeRateService.refresh());
    }
}
