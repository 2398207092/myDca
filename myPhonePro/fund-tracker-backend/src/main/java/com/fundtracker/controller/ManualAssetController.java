package com.fundtracker.controller;

import com.fundtracker.model.dto.*;
import com.fundtracker.service.ManualAssetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/manual-assets")
@RequiredArgsConstructor
public class ManualAssetController {

    private final ManualAssetService manualAssetService;

    @GetMapping
    public ApiResponse<List<ManualAssetDTO>> listManualAssets(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(manualAssetService.listManualAssets(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ManualAssetDTO> getManualAsset(@PathVariable String id,
                                                       HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(manualAssetService.getManualAsset(id, userId));
    }

    @PostMapping
    public ApiResponse<ManualAssetDTO> createManualAsset(@Valid @RequestBody CreateManualAssetReq req,
                                                          HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success("创建成功", manualAssetService.createManualAsset(req, userId));
    }

    @PutMapping("/{id}")
    public ApiResponse<ManualAssetDTO> updateManualAsset(@PathVariable String id,
                                                          @Valid @RequestBody UpdateManualAssetReq req,
                                                          HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success("更新成功", manualAssetService.updateManualAsset(id, req, userId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteManualAsset(@PathVariable String id,
                                                HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        manualAssetService.deleteManualAsset(id, userId);
        return ApiResponse.success(null);
    }
}
