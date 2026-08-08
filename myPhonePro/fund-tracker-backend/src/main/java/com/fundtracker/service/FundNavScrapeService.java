package com.fundtracker.service;

import com.fundtracker.model.entity.FundNavRecord;
import com.fundtracker.repository.FundNavRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基金历史净值爬取服务
 * 从天天基金 pingzhongdata/{code}.js 抓取净值数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundNavScrapeService {

    private final FundNavRecordRepository navRecordRepository;

    /** 天天基金净值数据接口（必须 HTTPS，防止中间人篡改） */
    private static final String PINGZHONG_DATA_URL = "https://fund.eastmoney.com/pingzhongdata/%s.js";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    /** 外部 HTTP 抓取重试策略：最多 3 次，指数退避 500ms → 1s → 2s */
    private static final RetryTemplate HTTP_RETRY = RetryTemplate.builder()
            .maxAttempts(3)
            .exponentialBackoff(500, 2, 5000)
            .retryOn(IOException.class)
            .build();

    /**
     * 抓取 pingzhongdata JS 内容（带重试）
     * 重试耗尽返回 null，由调用方决定是否降级到缓存
     */
    private String fetchPingZhongDataWithRetry(String fundCode, int timeoutMs) {
        String url = String.format(PINGZHONG_DATA_URL, fundCode);
        try {
            return HTTP_RETRY.execute(context -> Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .ignoreContentType(true)
                    .timeout(timeoutMs)
                    .execute()
                    .body());
        } catch (Exception e) {
            log.warn("基金 {} 净值抓取重试 {} 次后仍失败: {}（可能为过期数据）", fundCode, 3, e.getMessage());
            return null;
        }
    }

    /**
     * 抓取并保存指定基金的历史净值（最近5年）
     *
     * @param fundCode 基金代码
     * @return 最新净值信息（用于更新持仓市值）
     */
    public LatestNavResult fetchAndSaveNavRecords(String fundCode) {
        try {
            String jsContent = fetchPingZhongDataWithRetry(fundCode, 15000);
            if (jsContent == null) {
                // 重试耗尽：降级返回数据库缓存值，标记可能过期
                LatestNavResult cached = getLatestNavFromDb(fundCode);
                if (cached != null) {
                    log.warn("基金 {} 使用缓存净值（可能过期）: {} @ {}", fundCode, cached.unitNav(), cached.navDate());
                    return cached;
                }
                return null;
            }

            // 用 indexOf 定位 Data_netWorthTrend 数组
            String marker = "Data_netWorthTrend";
            int start = jsContent.indexOf(marker);
            if (start < 0) {
                log.warn("基金 {} Data_netWorthTrend 未找到", fundCode);
                return null;
            }
            start = jsContent.indexOf('[', start);
            if (start < 0) return null;
            int end = jsContent.indexOf("];", start);
            if (end < 0) return null;
            
            String arrayContent = jsContent.substring(start + 1, end);

            // 解析所有 "x":timestamp,"y":value 对
            Pattern itemPattern = Pattern.compile("\"x\":(\\d+),\"y\":([\\d.]+)");
            Matcher itemMatcher = itemPattern.matcher(arrayContent);

            // 计算 1 年前的时间戳
            long oneYearAgo = System.currentTimeMillis() - 1L * 365 * 24 * 3600 * 1000;

            List<FundNavRecord> toSave = new ArrayList<>();
            String latestX = null, latestY = null;

            while (itemMatcher.find()) {
                String xStr = itemMatcher.group(1);
                String yStr = itemMatcher.group(2);
                long timestamp = Long.parseLong(xStr);

                // 只记录最近1年
                if (timestamp < oneYearAgo) {
                    continue;
                }

                LocalDate navDate = Instant.ofEpochMilli(timestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
                BigDecimal unitNav = new BigDecimal(yStr);

                // 跳过已存在的记录
                if (!navRecordRepository.existsByFundCodeAndNavDate(fundCode, navDate)) {
                    FundNavRecord record = FundNavRecord.builder()
                            .id(UUID.randomUUID().toString().replace("-", ""))
                            .fundCode(fundCode)
                            .navDate(navDate)
                            .unitNav(unitNav)
                            .source("scrape")
                            .build();
                    toSave.add(record);
                }

                // 记录最后一条（最新的）
                latestX = xStr;
                latestY = yStr;
            }

            // 批量保存新记录
            if (!toSave.isEmpty()) {
                navRecordRepository.saveAll(toSave);
                log.info("基金 {} 新增保存 {} 条净值记录", fundCode, toSave.size());
            } else {
                log.info("基金 {} 净值数据已是最新，无需更新", fundCode);
            }

            // 返回最新净值
            if (latestX != null && latestY != null) {
                LocalDate latestDate = Instant.ofEpochMilli(Long.parseLong(latestX))
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
                return new LatestNavResult(
                        new BigDecimal(latestY),
                        latestDate,
                        toSave.size()
                );
            }

            return null;

        } catch (Exception e) {
            log.warn("抓取基金 {} 净值失败: {}", fundCode, e.getMessage());
            return null;
        }
    }

    /**
     * 从数据库获取最新净值
     */
    public LatestNavResult getLatestNavFromDb(String fundCode) {
        Optional<FundNavRecord> latest = navRecordRepository.findTopByFundCodeOrderByNavDateDesc(fundCode);
        if (latest.isPresent()) {
            FundNavRecord record = latest.get();
            return new LatestNavResult(record.getUnitNav(), record.getNavDate(), 0);
        }
        return null;
    }

    /**
     * 获取指定日期之前（含当天）最近一条净值
     */
    public LatestNavResult getLatestNavBefore(String fundCode, LocalDate date) {
        Optional<FundNavRecord> record = navRecordRepository
                .findTopByFundCodeAndNavDateLessThanEqualOrderByNavDateDesc(fundCode, date);
        if (record.isPresent()) {
            FundNavRecord r = record.get();
            return new LatestNavResult(r.getUnitNav(), r.getNavDate(), 0);
        }
        return null;
    }

    /**
     * 增量更新指定基金净值（只抓取最新一条，然后从数据库获取最新数据）
     */
    public LatestNavResult incrementalUpdate(String fundCode) {
        // 先尝试抓取最新数据
        try {
            String jsContent = fetchPingZhongDataWithRetry(fundCode, 10000);
            if (jsContent == null) {
                log.warn("增量更新基金 {} 净值抓取失败，回退数据库缓存（可能过期）", fundCode);
                return getLatestNavFromDb(fundCode);
            }

            // 用 indexOf 定位 Data_netWorthTrend 数组
            String marker = "Data_netWorthTrend";
            int start = jsContent.indexOf(marker);
            if (start < 0) return null;
            start = jsContent.indexOf('[', start);
            if (start < 0) return null;
            int end = jsContent.indexOf("];", start);
            if (end < 0) return null;
            
            String arrayContent = jsContent.substring(start + 1, end);
            Pattern itemPattern = Pattern.compile("\"x\":(\\d+),\"y\":([\\d.]+)");
            Matcher itemMatcher = itemPattern.matcher(arrayContent);

            // 检查数据库中最新的日期
            Optional<FundNavRecord> latestInDb = navRecordRepository.findTopByFundCodeOrderByNavDateDesc(fundCode);
            LocalDate maxDateInDb = latestInDb.map(FundNavRecord::getNavDate).orElse(null);
            // 1 年前时间戳，用于首次写入时限制数据量
            long oneYearAgo = System.currentTimeMillis() - 1L * 365 * 24 * 3600 * 1000;

            List<FundNavRecord> toSave = new ArrayList<>();
            String latestX = null, latestY = null;

            while (itemMatcher.find()) {
                String xStr = itemMatcher.group(1);
                String yStr = itemMatcher.group(2);
                long timestamp = Long.parseLong(xStr);

                // 如果 DB 为空，只写近 1 年的数据
                if (maxDateInDb == null && timestamp < oneYearAgo) {
                    continue;
                }

                LocalDate navDate = Instant.ofEpochMilli(timestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
                BigDecimal unitNav = new BigDecimal(yStr);

                // 只保存比数据库中更新的记录
                if (maxDateInDb == null || navDate.isAfter(maxDateInDb)) {
                    if (!navRecordRepository.existsByFundCodeAndNavDate(fundCode, navDate)) {
                        FundNavRecord record = FundNavRecord.builder()
                                .id(UUID.randomUUID().toString().replace("-", ""))
                                .fundCode(fundCode)
                                .navDate(navDate)
                                .unitNav(unitNav)
                                .source("scrape")
                                .build();
                        toSave.add(record);
                    }
                }

                latestX = xStr;
                latestY = yStr;
            }

            if (!toSave.isEmpty()) {
                navRecordRepository.saveAll(toSave);
                log.info("基金 {} 增量更新 {} 条净值记录", fundCode, toSave.size());
            }
        } catch (Exception e) {
            log.warn("增量更新基金 {} 净值失败: {}", fundCode, e.getMessage());
        }

        // 从数据库返回最新数据
        return getLatestNavFromDb(fundCode);
    }

    /**
     * 抓取并保存所有指定基金代码的净值（首次创建持仓时调用）
     */
    public void fetchIfEmpty(String fundCode) {
        List<FundNavRecord> existing = navRecordRepository.findByFundCodeOrderByNavDateDesc(fundCode);
        if (existing.isEmpty()) {
            log.info("基金 {} 净值数据为空，开始抓取最近1年数据", fundCode);
            fetchAndSaveNavRecords(fundCode);
        } else {
            log.info("基金 {} 已有 {} 条净值记录，跳过首次拉取", fundCode, existing.size());
        }
    }

    /**
     * 最新净值结果
     */
    public record LatestNavResult(
            BigDecimal unitNav,
            LocalDate navDate,
            int newRecordCount
    ) {}
}
