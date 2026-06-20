package com.fundtracker.controller;

import com.fundtracker.model.dto.ApiResponse;
import com.fundtracker.model.dto.DividendEventDTO;
import com.fundtracker.service.DividendRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dividend-records")
@RequiredArgsConstructor
public class DividendRecordController {

    private final DividendRecordService dividendRecordService;

    @GetMapping
    public ApiResponse<List<DividendEventDTO>> getRecords(
            @RequestParam(required = false) String holdingId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(dividendRecordService.getRecords(holdingId, year, status));
    }
}
