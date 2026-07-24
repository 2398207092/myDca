package com.fundtracker.controller;

import com.fundtracker.model.dto.*;
import com.fundtracker.service.CoverageCategoryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coverage-categories")
@RequiredArgsConstructor
public class CoverageCategoryController {

    private final CoverageCategoryService coverageCategoryService;

    @GetMapping
    public ApiResponse<List<CoverageCategoryDTO>> listAll(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(coverageCategoryService.listAll(userId));
    }

    @PutMapping("/{id}")
    public ApiResponse<CoverageCategoryDTO> update(@PathVariable String id,
                                                    @RequestBody UpdateCoverageCategoryReq req,
                                                    HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(coverageCategoryService.update(id, req, userId));
    }
}
