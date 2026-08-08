package com.fundtracker.service;

import com.fundtracker.model.dto.DividendInfoDTO;
import com.fundtracker.model.entity.FundDividendRecord;
import com.fundtracker.repository.FundDividendRecordRepository;
import com.fundtracker.service.provider.DividendTableProvider;
import com.fundtracker.service.provider.EstablishDateProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抓取天天基金 fhsp 页面的分红数据，存入本地数据库
 * 用于替代原有的实时外部 API 调用方式
 * 外部数据源逻辑已下沉到 EstablishDateProvider / DividendTableProvider（#1 外部数据源层）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundDividendScrapeService {

    private final FundDividendRecordRepository recordRepository;
    private final EstablishDateProvider establishDateProvider;
    private final DividendTableProvider dividendTableProvider;

    private static final String FHSP_URL = "https://fundf10.eastmoney.com/fhsp_%s.html";

    /** 基金成立日期内存缓存（成立日期是静态属性，抓取一次后永久缓存） */
    private final Map<String, LocalDate> establishDateCache = new ConcurrentHashMap<>();

    /**
     * 获取基金成立日期（带内存缓存；委托 provider）。
     * 数据源：天天基金 FundMNBasicInformation 接口的 ESTABDATE 字段。
     *
     * @return 成立日期；获取失败返回 null
     */
    public LocalDate getEstablishDate(String fundCode) {
        if (establishDateCache.containsKey(fundCode)) {
            return establishDateCache.get(fundCode);
        }
        LocalDate establishDate = establishDateProvider.fetch(fundCode);
        // 注意：ConcurrentHashMap 不允许 null 值，抓取失败时不缓存（下次可重试）
        if (establishDate != null) {
            establishDateCache.put(fundCode, establishDate);
        }
        return establishDate;
    }

    /**
     * 抓取指定基金的全部分红数据（增量更新）
     * @return 本次新增的记录数
     */
    @Transactional
    public int scrapeAndSave(String fundCode) {
        try {
            // 按基金实际成立日期过滤分红记录（防代码复用脏数据）
            LocalDate establishDate = getEstablishDate(fundCode);

            // 分页抓取：部分老基金分红记录较多，页面会分页
            int saved = 0;
            int page = 1;
            boolean hasMore = true;

            while (hasMore && page <= 10) {
                String url = String.format(FHSP_URL, fundCode) + (page > 1 ? "?page=" + page : "");
                log.info("开始抓取分红数据: {} (第{}页)", url, page);

                // 委托 provider 抓取并解析（含成立日期过滤、重试）
                List<FundDividendRecord> pageRecords =
                        dividendTableProvider.fetchDividendRecords(fundCode, establishDate, page);

                if (pageRecords.isEmpty()) {
                    // 当前页无数据 → 全部抓完
                    break;
                }

                // 增量保存
                for (FundDividendRecord record : pageRecords) {
                    if (!recordRepository.existsByFundCodeAndExDate(fundCode, record.getExDate())) {
                        record.setId(UUID.randomUUID().toString());
                        recordRepository.save(record);
                        saved++;
                    }
                }

                // 检查是否有下一页（一页有20条以上继续尝试）
                hasMore = pageRecords.size() >= 20;
                page++;
            }

            if (saved > 0) {
                log.info("{} 分红抓取完成: 共新增{}条 (翻页{}页)", fundCode, saved, page - 1);
            } else {
                log.info("{} 分红数据已是最新", fundCode);
            }
            return saved;

        } catch (Exception e) {
            log.error("抓取 {} 分红数据失败: {}", fundCode, e.getMessage());
            return 0;
        }
    }

    /**
     * 抓取多只基金的分红数据
     */
    public int scrapeMultiple(List<String> fundCodes) {
        int total = 0;
        for (String code : fundCodes) {
            total += scrapeAndSave(code);
            // 避免请求过快被封
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        }
        return total;
    }

    /**
     * 从数据库获取指定基金的分红记录
     */
    public List<FundDividendRecord> getRecords(String fundCode) {
        return recordRepository.findByFundCodeOrderByExDateDesc(fundCode);
    }

    /**
     * 删除指定分红记录
     */
    @Transactional
    public void deleteRecord(String recordId) {
        recordRepository.deleteById(recordId);
    }

    /**
     * 从数据库获取指定基金的最新除权日
     */
    public Optional<LocalDate> getLatestExDate(String fundCode) {
        return recordRepository.findTopByFundCodeOrderByExDateDesc(fundCode)
                .map(FundDividendRecord::getExDate);
    }

    /**
     * 临时抓取基金分红数据并计算年均分红（不保存到数据库）
     * 用于添加持仓页面的实时展示
     * 
     * 新逻辑：
     * 1. 按时间跨度筛选分红记录
     * 2. 计算单次分红平均值（总分红 ÷ 分红次数）
     * 3. 识别分红频率（根据相邻分红日期间隔）
     * 4. 根据频率计算年均分红（单次均值 × 年预期次数）
     */
    public DividendInfoDTO fetchAndCalculate(String fundCode, String type, String horizon) {
        try {
            String url = String.format(FHSP_URL, fundCode);
            log.info("临时抓取分红数据用于展示: {}", url);

            List<FundDividendRecord> records = dividendTableProvider.fetchDividendRecords(
                    fundCode, getEstablishDate(fundCode), 1);
            if (records.isEmpty()) {
                log.warn("临时抓取 {} 未找到分红数据", fundCode);
                return noData(type);
            }

            // 按时间跨度筛选记录
            List<FundDividendRecord> filteredRecords = filterByHorizon(records, horizon);
            if (filteredRecords.isEmpty()) {
                log.warn("临时抓取 {} 在时间范围内未找到分红数据", fundCode);
                return noData(type);
            }

            // 计算总分红和有效分红次数
            BigDecimal sum = BigDecimal.ZERO;
            int count = 0;
            for (FundDividendRecord record : filteredRecords) {
                if (record.getDividendPerShare() != null && record.getDividendPerShare().compareTo(BigDecimal.ZERO) > 0) {
                    sum = sum.add(record.getDividendPerShare());
                    count++;
                }
            }

            if (count == 0) {
                return noData(type);
            }

            // 计算单次分红平均值
            BigDecimal avgPerShare = sum.divide(BigDecimal.valueOf(count), 6, java.math.RoundingMode.HALF_UP);

            // 识别分红频率并计算年均分红
            FrequencyResult freqResult = identifyFrequency(filteredRecords);
            BigDecimal annualDividend = avgPerShare.multiply(BigDecimal.valueOf(freqResult.expectedCountPerYear))
                    .setScale(4, java.math.RoundingMode.HALF_UP);

            String unitText = isFund(type) ? "每份" : "每股";
            return DividendInfoDTO.builder()
                    .annualDividendPerShare(annualDividend)
                    .unitText(unitText)
                    .source("scrape_temp")
                    .dividendFrequency(freqResult.frequency)
                    .dividendFrequencyDesc(freqResult.frequencyDesc)
                    .dividendCount(count)
                    .avgDividendPerShare(avgPerShare)
                    .build();

        } catch (Exception e) {
            log.warn("临时抓取 {} 分红数据失败: {}", fundCode, e.getMessage());
            return noData(type);
        }
    }

    /**
     * 根据时间跨度筛选分红记录
     */
    private List<FundDividendRecord> filterByHorizon(List<FundDividendRecord> records, String horizon) {
        int days = switch (horizon) {
            case "1y" -> 365;
            case "3y" -> 3 * 365;
            case "5y" -> 5 * 365;
            default -> 3 * 365;
        };

        LocalDate cutoffDate = LocalDate.now().minusDays(days);
        return records.stream()
                .filter(r -> r.getExDate() != null && !r.getExDate().isBefore(cutoffDate))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 识别分红频率
     * 通过计算相邻分红日的平均间隔来判断
     */
    private FrequencyResult identifyFrequency(List<FundDividendRecord> records) {
        if (records.size() < 2) {
            // 只有一条记录，无法判断频率
            return new FrequencyResult("irregular", "不定期分红", 1);
        }

        // 按除权日排序（从旧到新）
        List<LocalDate> dates = records.stream()
                .filter(r -> r.getExDate() != null)
                .map(FundDividendRecord::getExDate)
                .sorted()
                .collect(java.util.stream.Collectors.toList());

        if (dates.size() < 2) {
            return new FrequencyResult("irregular", "不定期分红", 1);
        }

        // 计算相邻日期的间隔天数
        long totalDays = 0;
        int intervals = 0;
        for (int i = 1; i < dates.size(); i++) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i));
            if (days > 0) {
                totalDays += days;
                intervals++;
            }
        }

        if (intervals == 0) {
            return new FrequencyResult("irregular", "不定期分红", 1);
        }

        double avgIntervalDays = (double) totalDays / intervals;
        log.info("平均分红间隔天数: {}", avgIntervalDays);

        // 根据平均间隔判断频率类型
        if (avgIntervalDays >= 25 && avgIntervalDays <= 35) {
            return new FrequencyResult("monthly", "月度分红", 12);
        } else if (avgIntervalDays >= 80 && avgIntervalDays <= 100) {
            return new FrequencyResult("quarterly", "季度分红", 4);
        } else if (avgIntervalDays >= 330 && avgIntervalDays <= 400) {
            return new FrequencyResult("yearly", "年度分红", 1);
        } else {
            // 不定期分红：按实际年均次数计算
            double years = java.time.temporal.ChronoUnit.DAYS.between(dates.get(0), dates.get(dates.size() - 1)) / 365.0;
            double annualCount = years > 0 ? dates.size() / years : dates.size();
            return new FrequencyResult("irregular", "不定期分红", Math.max(1, (int) Math.round(annualCount)));
        }
    }

    /**
     * 分红频率识别结果
     */
    private static class FrequencyResult {
        final String frequency;
        final String frequencyDesc;
        final int expectedCountPerYear;

        FrequencyResult(String frequency, String frequencyDesc, int expectedCountPerYear) {
            this.frequency = frequency;
            this.frequencyDesc = frequencyDesc;
            this.expectedCountPerYear = expectedCountPerYear;
        }
    }

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

    /**
     * 根据分红记录列表计算年均分红（使用频率识别逻辑）
     * @param records 已筛选的分红记录
     * @param type 类型：基金/ETF/股票
     * @return 包含频率信息的分红DTO
     */
    public DividendInfoDTO calculateWithFrequency(List<FundDividendRecord> records, String type) {
        if (records.isEmpty()) {
            return noData(type);
        }

        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (FundDividendRecord record : records) {
            if (record.getDividendPerShare() != null && record.getDividendPerShare().compareTo(BigDecimal.ZERO) > 0) {
                sum = sum.add(record.getDividendPerShare());
                count++;
            }
        }

        if (count == 0) {
            return noData(type);
        }

        BigDecimal avgPerShare = sum.divide(BigDecimal.valueOf(count), 6, java.math.RoundingMode.HALF_UP);
        FrequencyResult freqResult = identifyFrequency(records);
        BigDecimal annualDividend = avgPerShare.multiply(BigDecimal.valueOf(freqResult.expectedCountPerYear))
                .setScale(4, java.math.RoundingMode.HALF_UP);

        String unitText = isFund(type) ? "每份" : "每股";
        return DividendInfoDTO.builder()
                .annualDividendPerShare(annualDividend)
                .unitText(unitText)
                .source("database")
                .dividendFrequency(freqResult.frequency)
                .dividendFrequencyDesc(freqResult.frequencyDesc)
                .dividendCount(count)
                .avgDividendPerShare(avgPerShare)
                .build();
    }

    private double getYearsForHorizon(String horizon) {
        switch (horizon) {
            case "1y": return 1.0;
            case "3y": return 3.0;
            case "5y": return 5.0;
            default: return 3.0;
        }
    }
}