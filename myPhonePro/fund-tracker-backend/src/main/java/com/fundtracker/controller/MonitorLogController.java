package com.fundtracker.controller;

import com.fundtracker.model.dto.ApiResponse;
import com.fundtracker.service.AdminAccessService;
import com.fundtracker.service.MonitorLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 每日监控日志查询接口
 * 读取 logback 输出的监控日志文件（logs/monitor/），返回结构化的可读内容。
 * 前端入口：我的页面 - 数据工具 - 每日监控日志
 */
@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorLogController {

    private final MonitorLogService monitorLogService;
    private final AdminAccessService adminAccessService;

    /**
     * 获取有监控日志的日期列表
     */
    @GetMapping("/dates")
    public ApiResponse<List<String>> getAvailableDates(HttpServletRequest request) {
        adminAccessService.check((String) request.getAttribute("userId"));
        return ApiResponse.success(monitorLogService.getAvailableDates());
    }

    /**
     * 获取指定日期的监控日志内容（结构化）
     */
    @GetMapping("/content")
    public ApiResponse<MonitorLogService.MonitorLogContent> getMonitorContent(
            @RequestParam String date, HttpServletRequest request) {
        adminAccessService.check((String) request.getAttribute("userId"));
        // 校验日期格式
        if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return ApiResponse.error(400, "日期格式错误，应为 YYYY-MM-DD");
        }
        return ApiResponse.success(monitorLogService.getContent(date));
    }
}
