package com.fundtracker.controller;

import com.fundtracker.model.dto.ApiResponse;
import com.fundtracker.model.dto.AssetHistoryDTO;
import com.fundtracker.model.dto.AssetOverviewDTO;
import com.fundtracker.service.AssetOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/asset-overview")
@RequiredArgsConstructor
public class AssetOverviewController {

    private final AssetOverviewService assetOverviewService;

    @GetMapping
    public ApiResponse<AssetOverviewDTO> getOverview() {
        return ApiResponse.success(assetOverviewService.getOverview());
    }

    @GetMapping("/history")
    public ApiResponse<AssetHistoryDTO> getHistory(
            @RequestParam(defaultValue = "week") String range) {
        return ApiResponse.success(assetOverviewService.getHistory(range));
    }

    @PostMapping("/snapshot")
    public ApiResponse<Void> takeSnapshot() {
        assetOverviewService.snapshotToday();
        return ApiResponse.success(null);
    }
}
