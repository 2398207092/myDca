package com.fundtracker.controller;

import com.fundtracker.model.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 审计日志查询接口
 * 读取 logback 输出的审计日志文件，返回结构化的可读内容
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditLogController {

    /** 审计日志目录 */
    private static final String AUDIT_LOG_DIR = "logs/audit";

    /** 日志行解析正则：2026-06-29 03:00:00 [INFO] 消息内容 */
    private static final Pattern LOG_PATTERN =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} \\[(ERROR|WARN|INFO)] (.+)$");

    /** 仅保留审计结果行，过滤掉分隔线 */
    private static final List<String> SEPARATOR_MARKERS = List.of(
            "===== 开始数据对账 =====",
            "===== 数据对账完成"
    );

    /**
     * 获取有审计日志的日期列表
     */
    @GetMapping("/dates")
    public ApiResponse<List<String>> getAvailableDates() {
        File dir = new File(AUDIT_LOG_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            return ApiResponse.success(List.of());
        }

        List<String> dates = new ArrayList<>();

        // 1. 当天日志（audit.log）
        File currentLog = new File(dir, "audit.log");
        if (currentLog.exists() && currentLog.length() > 0) {
            dates.add(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        }

        // 2. 历史日志（audit.YYYY-MM-DD.log）
        File[] historyFiles = dir.listFiles((d, name) ->
                name.matches("audit\\.\\d{4}-\\d{2}-\\d{2}\\.log"));
        if (historyFiles != null) {
            for (File f : historyFiles) {
                // 从文件名提取日期：audit.2026-06-28.log → 2026-06-28
                String date = f.getName().replace("audit.", "").replace(".log", "");
                if (!dates.contains(date)) {
                    dates.add(date);
                }
            }
        }

        Collections.sort(dates, Collections.reverseOrder());
        return ApiResponse.success(dates);
    }

    /**
     * 获取指定日期的审计日志内容（结构化）
     */
    @GetMapping("/content")
    public ApiResponse<AuditContent> getAuditContent(@RequestParam String date) {
        // 校验日期格式
        if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return ApiResponse.error(400, "日期格式错误，应为 YYYY-MM-DD");
        }

        File logFile = resolveLogFile(date);
        if (logFile == null || !logFile.exists()) {
            return ApiResponse.success(new AuditContent(date, false, 0, 0, "", List.of()));
        }

        try {
            List<String> lines = Files.readAllLines(logFile.toPath());
            AuditContent content = parseLines(date, lines);
            return ApiResponse.success(content);
        } catch (IOException e) {
            return ApiResponse.error(500, "读取审计日志失败: " + e.getMessage());
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 根据日期解析对应的日志文件路径
     */
    private File resolveLogFile(String date) {
        File dir = new File(AUDIT_LOG_DIR);
        if (!dir.exists()) return null;

        LocalDate targetDate = LocalDate.parse(date);
        LocalDate today = LocalDate.now();

        if (targetDate.equals(today)) {
            // 当天：读 audit.log
            File f = new File(dir, "audit.log");
            return f.exists() ? f : null;
        } else {
            // 历史：读 audit.YYYY-MM-DD.log
            File f = new File(dir, "audit." + date + ".log");
            return f.exists() ? f : null;
        }
    }

    /**
     * 解析日志行，提取审计条目
     */
    private AuditContent parseLines(String date, List<String> lines) {
        List<AuditEntry> entries = new ArrayList<>();
        int errorCount = 0;
        int warningCount = 0;

        for (String line : lines) {
            Matcher matcher = LOG_PATTERN.matcher(line.trim());
            if (!matcher.matches()) continue;

            String level = matcher.group(1);
            String message = matcher.group(2).trim();

            // 跳过分隔线
            if (isSeparator(message)) continue;

            // 跳过 "开始数据对账" 和汇总行
            if (message.contains("=====")) continue;

            // "未发现异常" 行 → 作为 summary
            // "错误=X, 警告=Y" 行 → 已包含在 entries 中，但我们跳过汇总行
            if (message.contains("未发现异常")) continue;
            if (message.contains("错误=") && message.contains("警告=")) continue;

            switch (level) {
                case "ERROR":
                    errorCount++;
                    entries.add(new AuditEntry("error", cleanMessage(message)));
                    break;
                case "WARN":
                    warningCount++;
                    entries.add(new AuditEntry("warning", cleanMessage(message)));
                    break;
                default:
                    // INFO 级别的辅助消息也保留（如 ✅ 对账完成）
                    if (!message.isBlank()) {
                        entries.add(new AuditEntry("info", cleanMessage(message)));
                    }
                    break;
            }
        }

        // 生成总结文案
        String summary = buildSummary(errorCount, warningCount, entries);

        return new AuditContent(date, !entries.isEmpty(), errorCount, warningCount, summary, entries);
    }

    /**
     * 判断是否为分隔线
     */
    private boolean isSeparator(String message) {
        return SEPARATOR_MARKERS.stream().anyMatch(m -> message.contains(m) || message.contains("====="));
    }

    /**
     * 清理消息：去除行首尾空格，压缩多余空格
     */
    private String cleanMessage(String message) {
        return message.strip().replaceAll("\\s+", " ");
    }

    /**
     * 生成总结文案
     */
    private String buildSummary(int errorCount, int warningCount, List<AuditEntry> entries) {
        // 检查是否有 ✅ 完成信息
        boolean hasSuccess = entries.stream().anyMatch(
                e -> "info".equals(e.getLevel()) && e.getMessage().contains("✅"));
        if ((errorCount == 0 && warningCount == 0) || hasSuccess) {
            return "✅ 对账完成，未发现异常";
        }
        StringBuilder sb = new StringBuilder();
        if (errorCount > 0) {
            sb.append("❌ 发现 ").append(errorCount).append(" 个错误");
        }
        if (warningCount > 0) {
            if (!sb.isEmpty()) sb.append("，");
            sb.append("⚠️ ").append(warningCount).append(" 个警告");
        }
        return sb.toString();
    }

    // ==================== 内部 DTO ====================

    /** 审计内容响应 */
    static class AuditContent {
        private String date;
        private boolean hasContent;
        private int errorCount;
        private int warningCount;
        private String summary;
        private List<AuditEntry> entries;

        public AuditContent() {}
        public AuditContent(String date, boolean hasContent, int errorCount, int warningCount,
                            String summary, List<AuditEntry> entries) {
            this.date = date;
            this.hasContent = hasContent;
            this.errorCount = errorCount;
            this.warningCount = warningCount;
            this.summary = summary;
            this.entries = entries;
        }

        public String getDate() { return date; }
        public boolean isHasContent() { return hasContent; }
        public int getErrorCount() { return errorCount; }
        public int getWarningCount() { return warningCount; }
        public String getSummary() { return summary; }
        public List<AuditEntry> getEntries() { return entries; }
    }

    /** 单条审计条目 */
    static class AuditEntry {
        private String level;    // "error" | "warning" | "info"
        private String message;

        public AuditEntry() {}
        public AuditEntry(String level, String message) {
            this.level = level;
            this.message = message;
        }

        public String getLevel() { return level; }
        public String getMessage() { return message; }
    }
}
