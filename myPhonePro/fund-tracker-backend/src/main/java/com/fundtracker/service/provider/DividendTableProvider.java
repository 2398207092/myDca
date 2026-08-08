package com.fundtracker.service.provider;

import com.fundtracker.common.HttpClientWrapper;
import com.fundtracker.model.entity.FundDividendRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 天天基金分红送配页数据源 Provider（#1 外部数据源层）
 *
 * 内聚：URL、UA（浏览器 UA）、HTML 表格解析、成立日期过滤。
 * 抓取 fhsp_%s.html 分红送配详情表格，按基金成立日期过滤代码复用脏数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendTableProvider {

    private final HttpClientWrapper httpClient;

    private static final String FHSP_URL = "https://fundf10.eastmoney.com/fhsp_%s.html";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** fhsp 分红页 UA：HTML 页面需浏览器 UA 才能正常访问 */
    private static final Map<String, String> HEADERS = Map.of(
            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

    /**
     * 兜底过滤日期：仅当成立日期接口获取失败时使用（非配置项）。
     * 正常情况一律按每个基金的实际成立日期过滤，防止基金代码被复用导致抓取到前一只基金的分红脏数据。
     */
    public static final LocalDate FALLBACK_MIN_EX_DATE = LocalDate.of(2020, 1, 1);

    /**
     * 抓取指定基金分红页（含重试）
     *
     * @return 分红记录列表（已按成立日期过滤）；页面抓取失败返回空列表
     */
    public List<FundDividendRecord> fetchDividendRecords(String fundCode, LocalDate establishDate, int page) {
        String url = FHSP_URL.formatted(fundCode) + (page > 1 ? "?page=" + page : "");
        Document doc = httpClient.getDocument(url, HEADERS);
        if (doc == null) {
            log.warn("抓取分红页失败（重试耗尽）: {}", url);
            return List.of();
        }
        return parseDividendTable(doc, fundCode, establishDate);
    }

    /**
     * 判断除权日是否有效（用于过滤代码复用导致的脏分红数据）：
     * - 不能为 null，不能晚于今天（未来异常数据）
     * - 不能早于基金成立日期（成立日期前的分红属于代码复用前的旧基金）
     *
     * @param exDate         分红除权日
     * @param establishDate  基金成立日期（可能为 null，此时用兜底常量过滤）
     */
    public static boolean isValidExDate(LocalDate exDate, LocalDate establishDate) {
        if (exDate == null || exDate.isAfter(LocalDate.now())) {
            return false;
        }
        LocalDate effectiveMinDate = establishDate != null ? establishDate : FALLBACK_MIN_EX_DATE;
        return !exDate.isBefore(effectiveMinDate);
    }

    /**
     * 解析天天基金 fhsp 页面的分红送配详情表格
     */
    private List<FundDividendRecord> parseDividendTable(Document doc, String fundCode, LocalDate establishDate) {
        List<FundDividendRecord> records = new ArrayList<>();

        // 查找分红详情的表格 — 包含"权益登记日"列的表格
        Elements tables = doc.select("table.w782.comm.jjfl");
        if (tables.isEmpty()) {
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
                String cellText = cells.get(3).text();
                if (!cellText.contains("派现金")) continue;

                Integer year = parseInt(cells.get(0).text());
                LocalDate regDate = parseDate(cells.get(1).text());
                LocalDate exDate = parseDate(cells.get(2).text());
                BigDecimal perShare = parseBigDecimal(cellText);
                LocalDate payDate = parseDate(cells.get(4).text());

                if (!isValidExDate(exDate, establishDate)) {
                    if (log.isDebugEnabled()) {
                        log.debug("{} 跳过无效除权日: {} (成立日期: {})", fundCode, exDate, establishDate);
                    }
                    continue;
                }

                records.add(FundDividendRecord.builder()
                        .fundCode(fundCode)
                        .exDate(exDate)
                        .regDate(regDate)
                        .payDate(payDate)
                        .dividendPerShare(perShare)
                        .dividendYear(year)
                        .source("scrape")
                        .build());

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
            String numStr = text.trim().replaceAll("[^0-9]", "");
            return numStr.isEmpty() ? null : Integer.parseInt(numStr);
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
            String cleaned = text.trim();
            int idx = cleaned.indexOf("派现金");
            if (idx >= 0) {
                cleaned = cleaned.substring(idx + 3);
            }
            String numStr = cleaned.replaceAll("[^0-9.]", "");
            if (numStr.isEmpty()) return null;

            BigDecimal value = new BigDecimal(numStr);

            // 天天基金分红表可能以"每10份"为单位，需要折算为"每份"
            if (text.contains("每10份")) {
                value = value.divide(BigDecimal.TEN, 6, RoundingMode.HALF_UP);
            }

            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
