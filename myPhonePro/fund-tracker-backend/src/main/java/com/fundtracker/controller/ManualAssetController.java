package com.fundtracker.controller;

import com.fundtracker.model.dto.*;
import com.fundtracker.service.ManualAssetService;
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
    public ApiResponse<List<ManualAssetDTO>> listManualAssets() {
        return ApiResponse.success(manualAssetService.listManualAssets());
    }

    @GetMapping("/{id}")
    public ApiResponse<ManualAssetDTO> getManualAsset(@PathVariable String id) {
        return ApiResponse.success(manualAssetService.getManualAsset(id));
    }

    @PostMapping
    public ApiResponse<ManualAssetDTO> createManualAsset(@Valid @RequestBody CreateManualAssetReq req) {
        return ApiResponse.success("创建成功", manualAssetService.createManualAsset(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<ManualAssetDTO> updateManualAsset(@PathVariable String id,
                                                          @Valid @RequestBody UpdateManualAssetReq req) {
        return ApiResponse.success("更新成功", manualAssetService.updateManualAsset(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteManualAsset(@PathVariable String id) {
        manualAssetService.deleteManualAsset(id);
        return ApiResponse.success(null);
    }
}
