package com.fundtracker.controller;

import com.fundtracker.model.dto.*;
import com.fundtracker.service.DividendEventSyncService;
import com.fundtracker.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final DividendEventSyncService dividendEventSyncService;

    @GetMapping
    public ApiResponse<List<DividendEventDTO>> listEvents(
            @RequestParam(required = false) String holdingId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(
                eventService.listEvents(holdingId, month, dateFrom, dateTo, type, status));
    }

    @GetMapping("/date/{date}")
    public ApiResponse<List<DividendEventDTO>> getEventsByDate(@PathVariable String date) {
        return ApiResponse.success(eventService.getEventsByDate(date));
    }

    @PostMapping
    public ApiResponse<DividendEventDTO> createEvent(@Valid @RequestBody CreateEventReq req) {
        return ApiResponse.success("创建成功", eventService.createEvent(req));
    }

    @PutMapping("/{id}/distribute")
    public ApiResponse<DividendEventDTO> markDistributed(@PathVariable String id) {
        return ApiResponse.success("已到账", eventService.markDistributed(id));
    }

    @PutMapping("/{id}/cancel")
    public ApiResponse<CancelEventResp> cancelEvent(@PathVariable String id) {
        return ApiResponse.success(eventService.cancelEvent(id));
    }

    @PostMapping("/sync/{fundCode}")
    public ApiResponse<Map<String, Object>> syncEventsByFund(@PathVariable String fundCode) {
        int created = dividendEventSyncService.syncEventsForFund(fundCode);
        return ApiResponse.success(Map.of("fundCode", fundCode, "created", created));
    }

    @PostMapping("/sync-all")
    public ApiResponse<Map<String, Object>> syncAllEvents() {
        int total = dividendEventSyncService.syncAllEvents();
        return ApiResponse.success(Map.of("totalCreated", total));
    }
}
