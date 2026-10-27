package com.fundtracker.controller;

import com.fundtracker.model.dto.ApiResponse;
import com.fundtracker.model.dto.CryptoPriceDTO;
import com.fundtracker.model.dto.RefreshCryptoPricesResp;
import com.fundtracker.service.CryptoPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/crypto")
@RequiredArgsConstructor
public class CryptoPriceController {

    private final CryptoPriceService cryptoPriceService;

    /** 手动刷新价格（前端点「刷新」时调用） */
    @PostMapping("/refresh-prices")
    public ApiResponse<RefreshCryptoPricesResp> refresh() {
        return ApiResponse.success(cryptoPriceService.refresh());
    }

    /** 读当前价格，不发请求 */
    @GetMapping("/prices")
    public ApiResponse<List<CryptoPriceDTO>> prices() {
        return ApiResponse.success(cryptoPriceService.currentPrices());
    }
}
