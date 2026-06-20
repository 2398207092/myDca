package com.fundtracker.controller;

import com.fundtracker.model.dto.ApiResponse;
import com.fundtracker.model.dto.DashboardDTO;
import com.fundtracker.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ApiResponse<DashboardDTO> getDashboard() {
        return ApiResponse.success(dashboardService.getDashboard());
    }
}
