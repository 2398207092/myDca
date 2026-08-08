package com.fundtracker.service;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 每日监控日志服务
 *
 * 所有定时任务执行时调用 record() 写入结构化日志（logs/monitor/），
 * 前端"我的页面 - 数据工具 - 每日监控日志"弹窗统一查看。
 *
 * 日志行格式：任务名|结果(SUCCESS|FAIL)|耗时ms|详情
 * 示例：分红数据刷新|SUCCESS|2345|新增3条,同步30条事件
 */
@Slf4j
@Service
public class MonitorLogService {

    /** 独立的 monitor logger，输出到 logs/monitor/（与业务日志隔离） */
    private static final Logger MONITOR = LoggerFactory.getLogger("com.fundtracker.monitor");

    /** 监控日志目录 */
    private static final String MONITOR_LOG_DIR = "logs/monitor";

    private static final DateTimeFormatter FILE_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 日志行解析正则：任务名|结果|耗时|详情 */
    private static final Pattern LOG_PATTERN =
            Pattern.compile("^([^|]+)\\|(SUCCESS|FAIL)\\|(\\d+)\\|(.*)$");

    /**
     * 记录一次定时任务执行结果
     *
     * @param taskName 任务名（如"分红数据刷新"）
     * @param success  是否成功
     * @param durationMs 耗时毫秒
     * @param detail   详情描述
     */
    public void record(String taskName, boolean success, long durationMs, String detail) {
        String result = success ? "SUCCESS" : "FAIL";
        String line = String.format("%s|%s|%d|%s", taskName, result, durationMs, detail);
        if (success) {
            MONITOR.info(line);
        } else {
            MONITOR.error(line);
        }
    }

    /**
     * 获取有监控日志的日期列表（最近30天）
     */
    public List<String> getAvailableDates() {
        File dir = new File(MONITOR_LOG_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            return List.of();
        }

        List<String> dates = new ArrayList<>();

        // 当天日志（monitor.log）
        File currentLog = new File(dir, "monitor.log");
        if (currentLog.exists()) {
            dates.add(LocalDate.now().format(FILE_DATE_FMT));
        }

        // 历史日志（monitor.yyyy-MM-dd.log），最多 30 天
        File[] files = dir.listFiles((d, name) -> name.matches("monitor\\.\\d{4}-\\d{2}-\\d{2}\\.log"));
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                String date = name.substring("monitor.".length(), name.length() - ".log".length());
                dates.add(date);
            }
        }

        Collections.sort(dates);
        Collections.reverse(dates);
        return dates.stream().limit(30).toList();
    }

    /**
     * 获取指定日期的监控日志内容（结构化解析）
     */
    public MonitorLogContent getContent(String date) {
        // 先解析目标日期
        LocalDate targetDate;
        try {
            targetDate = LocalDate.parse(date, FILE_DATE_FMT);
        } catch (Exception e) {
            return MonitorLogContent.empty(date, 1, 0, "日期格式错误");
        }

        List<MonitorLogEntry> entries = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        // 当天日志文件：monitor.log；历史：monitor.yyyy-MM-dd.log
        String filename = targetDate.equals(LocalDate.now())
                ? "monitor.log"
                : "monitor." + date + ".log";
        Path filePath = Path.of(MONITOR_LOG_DIR, filename);
        if (!Files.exists(filePath)) {
            return MonitorLogContent.empty(date, 0, 0, "该日期无监控日志");
        }

        try {
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            for (String line : lines) {
                // 去掉 logback 前缀 "2026-08-08 19:00:00 [INFO] " 或 "[ERROR] "
                String stripped = stripLogPrefix(line);
                Matcher m = LOG_PATTERN.matcher(stripped);
                if (!m.find()) continue;

                String taskName = m.group(1);
                String result = m.group(2);
                long durationMs = Long.parseLong(m.group(3));
                String detail = m.group(4);

                boolean success = "SUCCESS".equals(result);
                if (success) successCount++; else failCount++;

                entries.add(new MonitorLogEntry(taskName, success, durationMs, detail));
            }
        } catch (IOException e) {
            log.warn("读取监控日志失败: {}", e.getMessage());
            return MonitorLogContent.empty(date, 0, 0, "读取日志失败: " + e.getMessage());
        }

        return new MonitorLogContent(date, entries.size(), failCount, buildSummary(successCount, failCount), entries);
    }

    private String stripLogPrefix(String line) {
        // 格式: 2026-08-08 19:00:00 [INFO] 消息 或 2026-08-08 19:00:00 [ERROR] 消息
        int bracketEnd = line.indexOf("] ");
        if (bracketEnd > 0 && line.contains("[INFO]") || bracketEnd > 0 && line.contains("[ERROR]")) {
            int idx = Math.max(line.indexOf("[INFO] "), line.indexOf("[ERROR] "));
            if (idx >= 0) {
                return line.substring(idx + "[INFO] ".length());
            }
        }
        return line;
    }

    private String buildSummary(int successCount, int failCount) {
        if (failCount > 0) {
            return String.format("%d 项任务执行，%d 项失败，%d 项成功", successCount + failCount, failCount, successCount);
        }
        return String.format("%d 项任务全部执行成功", successCount);
    }

    // ==================== DTO ====================

    public record MonitorLogEntry(String taskName, boolean success, long durationMs, String detail) {}

    public record MonitorLogContent(
            String date, int totalCount, int failCount, String summary, List<MonitorLogEntry> entries) {
        static MonitorLogContent empty(String date, int totalCount, int failCount, String summary) {
            return new MonitorLogContent(date, totalCount, failCount, summary, List.of());
        }
    }
}
