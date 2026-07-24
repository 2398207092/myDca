package com.fundtracker.controller;

import com.fundtracker.model.dto.AnnualizedReturnDTO;
import com.fundtracker.model.dto.ApiResponse;
import com.fundtracker.model.dto.HoldingDiffDTO;
import com.fundtracker.model.dto.HoldingSeriesDTO;
import com.fundtracker.model.dto.TotalAssetSeriesDTO;
import com.fundtracker.service.HoldingSnapshotService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 持仓级资产历史记录 API。
 * 路由前缀：/api/asset-history
 * 所有接口需要 AuthInterceptor 认证（不在白名单中）。
 */
@RestController
@RequestMapping("/api/asset-history")
@RequiredArgsConstructor
public class HoldingSnapshotController {

    private final HoldingSnapshotService holdingSnapshotService;

    /** 总资产走势（市值/份额/收益三条线） */
    @GetMapping("/overview")
    public ApiResponse<TotalAssetSeriesDTO> getOverview(
            HttpServletRequest request,
            @RequestParam(defaultValue = "month") String range) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(holdingSnapshotService.getTotalAssetSeries(range, userId));
    }

    /** 单持仓走势 */
    @GetMapping("/holding/{holdingId}")
    public ApiResponse<HoldingSeriesDTO> getHoldingSeries(
            HttpServletRequest request,
            @PathVariable String holdingId,
            @RequestParam(defaultValue = "month") String range) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(holdingSnapshotService.getHoldingSeries(holdingId, range, userId));
    }

    /** 单持仓 vs 上期变化 */
    @GetMapping("/holding/{holdingId}/diff")
    public ApiResponse<HoldingDiffDTO> getHoldingDiff(
            HttpServletRequest request,
            @PathVariable String holdingId) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(holdingSnapshotService.getHoldingDiff(holdingId, userId));
    }

    /** 单持仓年化收益率 */
    @GetMapping("/holding/{holdingId}/annualized")
    public ApiResponse<AnnualizedReturnDTO> getAnnualizedReturn(
            HttpServletRequest request,
            @PathVariable String holdingId) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(holdingSnapshotService.getAnnualizedReturn(holdingId, userId));
    }

    /** 手动触发快照（调试用） */
    @PostMapping("/snapshot")
    public ApiResponse<Void> triggerSnapshot(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        holdingSnapshotService.snapshotAllHoldings(userId);
        return ApiResponse.success("快照生成完成", null);
    }

    /** 快照记录列表（分页） */
    @GetMapping("/snapshots")
    public ApiResponse<Map<String, Object>> listSnapshots(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(holdingSnapshotService.listSnapshots(page, size));
    }
}
