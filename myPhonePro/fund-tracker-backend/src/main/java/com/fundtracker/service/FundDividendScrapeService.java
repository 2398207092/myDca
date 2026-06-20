package com.fundtracker.service;

import com.fundtracker.model.dto.ApiResponse;
import com.fundtracker.model.dto.DividendInfoDTO;
import com.fundtracker.model.entity.FundDividendRecord;
import com.fundtracker.repository.FundDividendRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 抓取天天基金 fhsp 页面的分红数据，存入本地数据库
 * 用于替代原有的实时外部 API 调用方式
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundDividendScrapeService {

    private final FundDividendRecordRepository recordRepository;

    private static final String FHSP_URL = "https://fundf10.eastmoney.com/fhsp_%s.html";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 抓取指定基金的全部分红数据（增量更新）
     * @return 本次新增的记录数
     */
    @Transactional
    public int scrapeAndSave(String fundCode) {
        try {
            String url = String.format(FHSP_URL, fundCode);
            log.info("开始抓取分红数据: {}", url);

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();

            // 查找分红送配详情表格
            List<FundDividendRecord> records = parseDividendTable(doc, fundCode);

            if (records.isEmpty()) {
                log.warn("{} 未找到分红数据", fundCode);
                return 0;
            }

            // 增量保存
            int saved = 0;
            for (FundDividendRecord record : records) {
                if (!recordRepository.existsByFundCodeAndExDate(fundCode, record.getExDate())) {
                    record.setId(UUID.randomUUID().toString());
                    recordRepository.save(record);
                    saved++;
                }
            }

            log.info("{} 分红抓取完成: 共{}条, 新增{}条", fundCode, records.size(), saved);
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

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();

            List<FundDividendRecord> records = parseDividendTable(doc, fundCode);
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

    // ==================== HTML 解析 ====================

    /**
     * 解析天天基金 fhsp 页面的分红送配详情表格
     */
    private List<FundDividendRecord> parseDividendTable(Document doc, String fundCode) {
        List<FundDividendRecord> records = new ArrayList<>();

        // 查找分红详情的表格 — 包含"权益登记日"列的表格
        Elements tables = doc.select("table.w782.comm.jjfl");
        if (tables.isEmpty()) {
            // 尝试其他选择器
            tables = doc.select("table:has(th:contains(每份分红))");
        }
        if (tables.isEmpty()) {
            tables = doc.select("table:has(th:contains(权益登记日))");
        }

        if (tables.isEmpty()) {
            log.warn("{} 未找到分红表格", fundCode);
            return records;
        }

        Element table = tables.first();
        Elements rows = table.select("tbody tr");

        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() < 5) continue;

            try {
                // 0: 年份, 1: 权益登记日, 2: 除息日, 3: 每份分红, 4: 分红发放日
                Integer year = parseInt(cells.get(0).text());
                LocalDate regDate = parseDate(cells.get(1).text());
                LocalDate exDate = parseDate(cells.get(2).text());
                BigDecimal perShare = parseBigDecimal(cells.get(3).text());
                LocalDate payDate = parseDate(cells.get(4).text());

                if (exDate == null) continue;

                FundDividendRecord record = FundDividendRecord.builder()
                        .fundCode(fundCode)
                        .exDate(exDate)
                        .regDate(regDate)
                        .payDate(payDate)
                        .dividendPerShare(perShare)
                        .dividendYear(year)
                        .source("scrape")
                        .build();

                records.add(record);

            } catch (Exception e) {
                log.warn("解析分红记录行失败: {}", e.getMessage());
            }
        }

        return records;
    }

    private Integer parseInt(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            // 提取数字部分（处理"2026年"这样的格式）
            String numStr = text.trim().replaceAll("[^0-9]", "");
            if (numStr.isEmpty()) return null;
            return Integer.parseInt(numStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDate(String text) {
        if (text == null || text.trim().isEmpty() || "--".equals(text.trim())) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim(), DATE_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String text) {
        if (text == null || text.trim().isEmpty() || "--".equals(text.trim())) {
            return null;
        }
        try {
            // 提取数字部分（处理"每份派现金0.0133元"这样的格式）
            String numStr = text.trim().replaceAll("[^0-9.]", "");
            if (numStr.isEmpty()) return null;
            return new BigDecimal(numStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}