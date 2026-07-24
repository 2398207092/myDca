package com.fundtracker.controller;

import com.fundtracker.model.dto.ApiResponse;
import com.fundtracker.model.dto.AssetHistoryDTO;
import com.fundtracker.model.dto.AssetOverviewDTO;
import com.fundtracker.service.AssetOverviewService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/asset-overview")
@RequiredArgsConstructor
public class AssetOverviewController {

    private final AssetOverviewService assetOverviewService;

    @GetMapping
    public ApiResponse<AssetOverviewDTO> getOverview(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(assetOverviewService.getOverview(userId));
    }

    @GetMapping("/history")
    public ApiResponse<AssetHistoryDTO> getHistory(
            @RequestParam(defaultValue = "week") String range,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(assetOverviewService.getHistory(range, userId));
    }

    @PostMapping("/snapshot")
    public ApiResponse<Void> takeSnapshot(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        assetOverviewService.snapshotToday(userId);
        return ApiResponse.success(null);
    }
}
