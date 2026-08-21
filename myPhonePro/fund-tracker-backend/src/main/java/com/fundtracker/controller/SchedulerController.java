package com.fundtracker.controller;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.ApiResponse;
import com.fundtracker.scheduler.DataAuditor;
import com.fundtracker.scheduler.DcaScheduler;
import com.fundtracker.scheduler.DividendAutoDistributeScheduler;
import com.fundtracker.scheduler.FundDividendScheduler;
import com.fundtracker.scheduler.FundNavScheduler;
import com.fundtracker.scheduler.HoldingSnapshotScheduler;
import com.fundtracker.service.AdminAccessService;
import com.fundtracker.service.MonitorLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 定时任务查看与手动触发接口（仅管理员）。
 * - GET  /api/scheduler/tasks       任务总览：名称、cron、下次执行、最近状态
 * - POST /api/scheduler/tasks/{id}/run  手动立即执行一次
 *
 * 手动触发与每日 @Scheduled 共用同一套任务方法，执行结果同样写入监控日志。
 * 为避免外部爬虫接口被频繁触发，同一任务手动触发带 5 分钟节流。
 */
@Slf4j
@RestController
@RequestMapping("/api/scheduler")
@RequiredArgsConstructor
public class SchedulerController {

    private final AdminAccessService adminAccessService;
    private final MonitorLogService monitorLogService;
    private final DataAuditor dataAuditor;
    private final FundDividendScheduler fundDividendScheduler;
    private final DividendAutoDistributeScheduler dividendAutoDistributeScheduler;
    private final DcaScheduler dcaScheduler;
    private final FundNavScheduler fundNavScheduler;
    private final HoldingSnapshotScheduler holdingSnapshotScheduler;

    /** 同一任务手动触发的最小间隔：5 分钟 */
    private static final long THROTTLE_MS = 5 * 60 * 1000L;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 任务元数据：id / 名称 / cron / 描述 */
    private record TaskMeta(String id, String name, String cron, String description) {}

    private static final List<TaskMeta> TASKS = List.of(
            new TaskMeta("audit", "数据对账审计", "0 0 3 * * ?", "每日一致性检查：市值、现金流水、份额、成本"),
            new TaskMeta("dividend_refresh", "分红数据刷新", "0 0 6 * * ?", "抓取分红数据并同步分红事件到日历"),
            new TaskMeta("dividend_distribute", "分红自动分发", "0 30 9 * * ?", "到期分红自动到账，开启复投则自动买入"),
            new TaskMeta("dca", "定投执行", "0 0 20 * * ?", "到期定投自动买入，并联动刷新持仓指标"),
            new TaskMeta("nav", "净值数据刷新", "0 0 22 * * ?", "抓取基金净值并刷新市值、成本、分红预测"),
            new TaskMeta("snapshot", "持仓快照", "0 30 23 1/5 * ?", "每 5 天生成持仓快照（1/6/11/16/21/26 日）")
    );

    /** 任务 id → 最近一次手动触发时间戳，用于节流 */
    private final Map<String, Long> lastManualRun = new ConcurrentHashMap<>();

    /**
     * 任务总览
     */
    @GetMapping("/tasks")
    public ApiResponse<List<TaskInfo>> listTasks(HttpServletRequest request) {
        adminAccessService.check((String) request.getAttribute("userId"));
        List<TaskInfo> infos = TASKS.stream().map(this::toInfo).toList();
        return ApiResponse.success(infos);
    }

    /**
     * 手动触发任务
     */
    @PostMapping("/tasks/{id}/run")
    public ApiResponse<RunResult> runTask(@PathVariable String id, HttpServletRequest request) {
        adminAccessService.check((String) request.getAttribute("userId"));
        TaskMeta meta = TASKS.stream().filter(t -> t.id().equals(id)).findFirst()
                .orElseThrow(() -> BusinessException.invalidParam("未知任务: " + id));

        // 节流：同一任务 5 分钟内只能手动触发一次
        long now = System.currentTimeMillis();
        Long last = lastManualRun.get(id);
        if (last != null && now - last < THROTTLE_MS) {
            long remainSec = (THROTTLE_MS - (now - last)) / 1000;
            throw new BusinessException(4001,
                    "手动触发过于频繁，请约 " + (remainSec / 60 + 1) + " 分钟后再试");
        }
        lastManualRun.put(id, now);

        long startMs = System.currentTimeMillis();
        boolean success = true;
        String detail;
        try {
            detail = execute(meta.id());
        } catch (Exception e) {
            success = false;
            detail = "失败: " + e.getMessage();
            log.error("手动触发任务 {} 失败", id, e);
        }
        long durationMs = System.currentTimeMillis() - startMs;

        // 任务方法内部已通过 MonitorLogService.record 记录执行结果；
        // 失败时补记一条，保证手动触发有反馈。
        if (!success) {
            monitorLogService.record(meta.name(), false, durationMs, detail);
        }

        return ApiResponse.success(new RunResult(meta.id(), meta.name(), success, durationMs, detail));
    }

    /** 调用对应定时任务方法（与 @Scheduled 同一入口） */
    private String execute(String id) {
        switch (id) {
            case "audit":
                dataAuditor.auditAll();
                return "数据对账完成";
            case "dividend_refresh":
                int total = fundDividendScheduler.refreshAllHoldingsDividendData();
                return "分红数据刷新完成，新增 " + total + " 条记录";
            case "dividend_distribute":
                dividendAutoDistributeScheduler.autoDistributeDividends();
                return "分红自动分发完成";
            case "dca":
                dcaScheduler.processDcaPlans();
                return "定投执行完成";
            case "nav":
                fundNavScheduler.refreshAllNav();
                return "净值数据刷新完成";
            case "snapshot":
                holdingSnapshotScheduler.snapshotTask();
                return "持仓快照生成完成";
            default:
                throw BusinessException.invalidParam("未知任务: " + id);
        }
    }

    /** 组装任务信息：下次执行时间 + 最近一次执行状态（读监控日志） */
    private TaskInfo toInfo(TaskMeta meta) {
        String nextRunAt = "";
        try {
            LocalDateTime next = CronExpression.parse(meta.cron()).next(LocalDateTime.now());
            if (next != null) {
                nextRunAt = next.format(TIME_FMT);
            }
        } catch (Exception e) {
            log.warn("解析 cron 失败 {}: {}", meta.cron(), e.getMessage());
        }

        MonitorLogService.MonitorLogEntry last = monitorLogService.getRecentEntryByTask(meta.name(), 7);
        return new TaskInfo(
                meta.id(),
                meta.name(),
                meta.cron(),
                meta.description(),
                nextRunAt,
                last != null ? last.success() : null,
                last != null ? last.timestamp() : null,
                last != null ? last.durationMs() : null,
                last != null ? last.detail() : null
        );
    }

    /** 任务信息（返回给前端） */
    public record TaskInfo(
            String id,
            String name,
            String cron,
            String description,
            String nextRunAt,
            Boolean lastSuccess,
            String lastRunAt,
            Long lastDurationMs,
            String lastDetail) {}

    /** 手动触发结果 */
    public record RunResult(String taskId, String taskName, boolean success, long durationMs, String detail) {}
}
