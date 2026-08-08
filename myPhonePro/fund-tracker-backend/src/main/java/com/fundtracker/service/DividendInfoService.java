package com.fundtracker.service;

import com.fundtracker.common.HttpClientWrapper;
import com.fundtracker.model.dto.DividendInfoDTO;
import com.fundtracker.model.entity.FundDividendRecord;
import com.fundtracker.repository.FundDividendRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 查询基金的历史分红数据，计算年均每份分红
 * 支持两种口径：
 *   - ex_date（按除权日）：取 Data_netWorthTrend 中 unitMoney>0 的记录求和
 *   - report_period（按报告期）：取时间段内累计净值增长值
 * 支持三种时间跨度：1y / 3y / 5y
 */
@Slf4j
@Service
public class DividendInfoService {

    private final FundDividendRecordRepository fundDividendRecordRepository;
    private final FundDividendScrapeService fundDividendScrapeService;
    private final HttpClientWrapper httpClient;

    /** pingzhongdata 净值 JS 接口 UA：HTML/JS 页面需浏览器 UA（与 fhsp 分红页一致） */
    private static final Map<String, String> PINGZHONG_HEADERS = Map.of(
            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Accept", "*/*");

    public DividendInfoService(FundDividendRecordRepository fundDividendRecordRepository,
                               FundDividendScrapeService fundDividendScrapeService,
                               HttpClientWrapper httpClient) {
        this.fundDividendRecordRepository = fundDividendRecordRepository;
        this.fundDividendScrapeService = fundDividendScrapeService;
        this.httpClient = httpClient;
    }

    public DividendInfoDTO getDividendInfo(String code, String type,
                                           String method, String horizon) {
        if (code == null || code.trim().isEmpty()) {
            return noData(type);
        }

        // 默认值
        if (method == null || method.isEmpty()) method = "ex_date";
        if (horizon == null || horizon.isEmpty()) horizon = "3y";

        long cutoffMs = getCutoffMillis(horizon);  // 截止时间的毫秒偏移
        String unitText = isFund(type) ? "每份" : "每股";

        try {
            if ("ex_date".equals(method)) {
                // ===== 统一口径：仅使用 本地数据库 + fhsp 页面 =====
                // 1. 优先本地数据库（含频率识别），与 FundDividendScrapeService 同口径
                DividendInfoDTO dbResult = calcFromLocalDbWithFrequency(code.trim(), cutoffMs, horizon, type);
                if (dbResult != null && !"none".equals(dbResult.getSource())) {
                    return dbResult;
                }
                // 2. 数据库无数据 → fhsp 页面临时抓取（同口径）
                DividendInfoDTO tempResult = fundDividendScrapeService.fetchAndCalculate(code.trim(), type, horizon);
                if (!"none".equals(tempResult.getSource())) {
                    return tempResult;
                }
                return noData(type);
            } else if ("report_period".equals(method)) {
                // 按报告期：取品中数据累计净值差（该口径无数据库来源，保留唯一路径）
                String jsData = fetchPingZhongData(code.trim());
                if (jsData != null) {
                    BigDecimal annualDividend = calcByReportPeriod(jsData, cutoffMs, horizon);
                    if (annualDividend != null && annualDividend.compareTo(BigDecimal.ZERO) > 0) {
                        return DividendInfoDTO.builder()
                                .annualDividendPerShare(annualDividend)
                                .unitText(unitText)
                                .source("api")
                                .build();
                    }
                    log.warn("{} 按报告期无分红数据 horizon={}，尝试临时抓取", code, horizon);
                } else {
                    log.warn("获取 {} 净值数据失败，降级到临时抓取兜底", code);
                }
                // 兜底：fhsp 页面临时抓取（统一口径）
                DividendInfoDTO tempResult = fundDividendScrapeService.fetchAndCalculate(code.trim(), type, horizon);
                if (!"none".equals(tempResult.getSource())) {
                    return tempResult;
                }
                return noData(type);
            } else {
                log.info("{} 自定义模式，尝试临时抓取", code);
                DividendInfoDTO tempResult = fundDividendScrapeService.fetchAndCalculate(code.trim(), type, horizon);
                if (!"none".equals(tempResult.getSource())) {
                    return tempResult;
                }
                return noData(type);
            }

        } catch (Exception e) {
            log.warn("获取分红数据异常 {}: {}", code, e.getMessage());
            return noData(type);
        }
    }

    // ===== 兼容旧调用（不带 method / horizon）=====
    public DividendInfoDTO getDividendInfo(String code, String type) {
        return getDividendInfo(code, type, "ex_date", "3y");
    }

    // ==================== 按除权日计算（数据库口径） ====================

    /**
     * 从本地数据库计算年均每份分红（包含频率识别）
     * 统一走 FundDividendScrapeService.calculateWithFrequency，与 fhsp 抓取同口径，
     * 避免不同入口算出不同结果（#9 数据源统一）
     */
    private DividendInfoDTO calcFromLocalDbWithFrequency(String code, long cutoffMs, String horizon, String type) {
        long now = System.currentTimeMillis();
        long threshold = now - cutoffMs;
        LocalDate afterDate = Instant.ofEpochMilli(threshold).atZone(ZoneId.systemDefault()).toLocalDate();

        List<FundDividendRecord> records = fundDividendRecordRepository
                .findByFundCodeAndExDateAfterOrderByExDateAsc(code, afterDate);

        if (records.isEmpty()) {
            log.info("本地数据库无 {} 的分红记录", code);
            return null;
        }

        return fundDividendScrapeService.calculateWithFrequency(records, type);
    }

    // ==================== 按报告期计算 ====================

    /**
     * 按报告期：取时间段首尾的累计净值差值，除以年数
     */
    private BigDecimal calcByReportPeriod(String js, long cutoffMs, String horizon) {
        List<AccRecord> records = parseACWorthTrend(js);
        log.info("calcByReportPeriod: 解析到 {} 条累计净值记录", records.size());
        if (records.isEmpty()) return null;

        long now = System.currentTimeMillis();
        long threshold = now - cutoffMs;

        // 找到最接近 threshold 的累计净值记录
        AccRecord startRec = null;
        for (AccRecord r : records) {
            if (r.timestamp >= threshold) {
                startRec = r;
                break;
            }
        }
        // 如果都早于 threshold，用最早记录
        if (startRec == null && !records.isEmpty()) {
            startRec = records.get(records.size() - 1);
        }
        // 最新的累计净值
        AccRecord endRec = records.get(0);

        if (startRec == null || endRec == null) return null;

        BigDecimal diff = endRec.accNav.subtract(startRec.accNav);
        log.info("按报告期: start={} accNav={}, end={} accNav={}, diff={}",
                startRec.timestamp, startRec.accNav, endRec.timestamp, endRec.accNav, diff);

        if (diff.compareTo(BigDecimal.ZERO) <= 0) return null;

        double years = getYearsForHorizon(horizon);
        return diff.divide(BigDecimal.valueOf(years), 4, RoundingMode.HALF_UP);
    }

    /**
     * 解析 Data_ACWorthTrend 数组
     * 格式: [[ts, accNav], ...]
     * 注意：数据从旧到新排列
     */
    private List<AccRecord> parseACWorthTrend(String js) {
        List<AccRecord> list = new ArrayList<>();
        Pattern p = Pattern.compile("var\\s+Data_ACWorthTrend\\s*=\\s*\\[([^;]+)\\]\\s*;", Pattern.DOTALL);
        Matcher m = p.matcher(js);
        if (!m.find()) return list;

        String content = m.group(1);
        // 逐个提取 [ts, val] 子数组
        int idx = 0;
        while (true) {
            int bracketStart = content.indexOf('[', idx);
            if (bracketStart < 0) break;
            int bracketEnd = content.indexOf(']', bracketStart);
            if (bracketEnd < 0) break;

            String item = content.substring(bracketStart + 1, bracketEnd);
            String[] parts = item.split(",");
            if (parts.length >= 2) {
                try {
                    long ts = Long.parseLong(parts[0].trim());
                    BigDecimal nav = new BigDecimal(parts[1].trim());
                    list.add(new AccRecord(ts, nav));
                } catch (Exception ignored) {}
            }
            idx = bracketEnd + 1;
        }
        return list;
    }

    // ==================== 工具方法 ====================

    private long getCutoffMillis(String horizon) {
        switch (horizon) {
            case "1y": return 365L * 24 * 3600 * 1000;
            case "3y": return 3L * 365 * 24 * 3600 * 1000;
            case "5y": return 5L * 365 * 24 * 3600 * 1000;
            default:   return 3L * 365 * 24 * 3600 * 1000;
        }
    }

    private double getYearsForHorizon(String horizon) {
        switch (horizon) {
            case "1y": return 1.0;
            case "3y": return 3.0;
            case "5y": return 5.0;
            default:   return 3.0;
        }
    }

    // ==================== 数据读取 ====================

    private String fetchPingZhongData(String code) {
        String urlStr = "https://fund.eastmoney.com/pingzhongdata/" + code + ".js";
        log.info("DividendInfo: 开始获取 {}", urlStr);
        String result = httpClient.getText(urlStr, PINGZHONG_HEADERS);
        if (result == null) {
            log.warn("DividendInfo: 获取 {} 重试 3 次后仍失败", code);
            return null;
        }
        int len = result.length();
        log.info("DividendInfo: 获取成功, {} chars", len);
        if (len < 100) {
            log.warn("DividendInfo: 内容过短 {} chars, 响应={}", len, HttpClientWrapper.truncate(result, 200));
            return null;
        }
        return result;
    }

    // ==================== 辅助方法 ====================

    private boolean isFund(String type) {
        if (type == null) return true;
        return type.equals("基金") || type.equals("ETF") || type.equals("fund");
    }

    private DividendInfoDTO noData(String type) {
        String unitText = isFund(type) ? "每份" : "每股";
        return DividendInfoDTO.builder()
                .annualDividendPerShare(BigDecimal.ZERO)
                .unitText(unitText)
                .source("none")
                .build();
    }

    // ==================== 内部数据结构 ====================

    private static class AccRecord {
        final long timestamp;
        final BigDecimal accNav;
        AccRecord(long ts, BigDecimal nav) {
            this.timestamp = ts;
            this.accNav = nav;
        }
    }
}
