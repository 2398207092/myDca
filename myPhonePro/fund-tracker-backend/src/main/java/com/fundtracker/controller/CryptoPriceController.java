package com.fundtracker.controller;

import com.fundtracker.model.dto.ApiResponse;
import com.fundtracker.model.dto.CryptoPriceDTO;
import com.fundtracker.model.dto.RefreshCryptoPricesResp;
import com.fundtracker.service.CryptoPriceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/crypto")
@RequiredArgsConstructor
public class CryptoPriceController {

    private final CryptoPriceService cryptoPriceService;

    /** 手动刷新价格（只刷当前用户，避免跨用户写库） */
    @PostMapping("/refresh-prices")
    public ApiResponse<RefreshCryptoPricesResp> refresh(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(cryptoPriceService.refresh(userId));
    }

    /** 读当前价格，不发请求 */
    @GetMapping("/prices")
    public ApiResponse<List<CryptoPriceDTO>> prices(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(cryptoPriceService.currentPrices(userId));
    }
}
