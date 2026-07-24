package com.fundtracker.controller;

import com.fundtracker.model.dto.ApiResponse;
import com.fundtracker.model.dto.ValueChangeDTO;
import com.fundtracker.service.ValueChangeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/holdings")
@RequiredArgsConstructor
public class ValueChangeController {

    private final ValueChangeService valueChangeService;

    @GetMapping("/value-change")
    public ApiResponse<ValueChangeDTO> getValueChange(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        ValueChangeDTO result = valueChangeService.getValueChange(userId);
        return ApiResponse.success(result);
    }
}
