package com.fundtracker.controller;

import com.fundtracker.model.dto.ApiResponse;
import com.fundtracker.model.dto.ValueChangeDTO;
import com.fundtracker.service.ValueChangeService;
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
    public ApiResponse<ValueChangeDTO> getValueChange() {
        ValueChangeDTO result = valueChangeService.getValueChange();
        return ApiResponse.success(result);
    }
}
