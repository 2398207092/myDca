package com.fundtracker.controller;

import com.fundtracker.model.dto.*;
import com.fundtracker.service.RiskTargetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/risk-targets")
@RequiredArgsConstructor
public class RiskTargetController {

    private final RiskTargetService riskTargetService;

    @GetMapping
    public ApiResponse<RiskTargetDTO> get(@RequestAttribute("userId") String userId) {
        return ApiResponse.success(riskTargetService.get(userId));
    }

    @PutMapping
    public ApiResponse<RiskTargetDTO> save(@RequestAttribute("userId") String userId,
                                           @RequestBody UpdateRiskTargetReq req) {
        return ApiResponse.success(riskTargetService.save(userId, req));
    }
}