package com.fundtracker.controller;

import com.fundtracker.model.dto.*;
import com.fundtracker.service.DividendEventSyncService;
import com.fundtracker.service.EventService;
import jakarta.servlet.http.HttpServletRequest;
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
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(
                eventService.listEvents(holdingId, month, dateFrom, dateTo, type, status, userId));
    }

    @GetMapping("/date/{date}")
    public ApiResponse<List<DividendEventDTO>> getEventsByDate(
            @PathVariable String date,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(eventService.getEventsByDate(date, userId));
    }

    @PostMapping
    public ApiResponse<DividendEventDTO> createEvent(
            @Valid @RequestBody CreateEventReq req,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success("创建成功", eventService.createEvent(req, userId));
    }

    @PutMapping("/{id}/distribute")
    public ApiResponse<DividendEventDTO> markDistributed(
            @PathVariable String id,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success("已到账", eventService.markDistributed(id, userId));
    }

    @PostMapping("/{id}/convert-to-reinvest")
    public ApiResponse<DividendEventDTO> convertToReinvest(
            @PathVariable String id,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success("已转为复投", eventService.convertToReinvest(id, userId));
    }

    @PutMapping("/{id}/cancel")
    public ApiResponse<CancelEventResp> cancelEvent(
            @PathVariable String id,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(eventService.cancelEvent(id, userId));
    }

    @PostMapping("/sync/{fundCode}")
    public ApiResponse<Map<String, Object>> syncEventsByFund(
            @PathVariable String fundCode,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        int created = dividendEventSyncService.syncEventsForFund(fundCode, userId);
        return ApiResponse.success(Map.of("fundCode", fundCode, "created", created));
    }

    @PostMapping("/sync-all")
    public ApiResponse<Map<String, Object>> syncAllEvents(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        int total = dividendEventSyncService.syncAllEvents(userId);
        return ApiResponse.success(Map.of("totalCreated", total));
    }
}
