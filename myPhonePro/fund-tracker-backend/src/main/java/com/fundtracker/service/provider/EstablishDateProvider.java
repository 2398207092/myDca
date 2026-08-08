package com.fundtracker.service.provider;

import com.fundtracker.common.HttpClientWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基金成立日期数据源 Provider（#1 外部数据源层）
 *
 * 内聚：URL、UA（必须 curl/）、解析逻辑、失败策略。
 *
 * 反爬注意（2026-08-08 事故）：FundMNBasicInformation 接口对浏览器 UA 返回反爬拦截
 * （190字节"网络繁忙"，ErrCode 61136403），只有 curl/ UA 能拿到真实数据。
 * 这就是"每个接口必须单独验证 UA"的典型例子——与其他天天基金接口（浏览器 UA）相反。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EstablishDateProvider {

    private final HttpClientWrapper httpClient;

    private static final String ESTAB_DATE_URL =
            "https://fundmobapi.eastmoney.com/FundMNewApi/FundMNBasicInformation?FCODE=%s&deviceid=Wap&plat=Wap&product=EFund&version=2.0.0";

    /** 成立日期接口 UA：实测必须 curl/，浏览器 UA 被反爬拦截 */
    private static final Map<String, String> HEADERS = Map.of(
            "User-Agent", "curl/8.0");

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern ESTABDATE_PATTERN = Pattern.compile("\"ESTABDATE\"\\s*:\\s*\"([0-9-]+)\"");

    /**
     * 获取基金成立日期
     *
     * @return 成立日期；获取失败返回 null
     */
    public LocalDate fetch(String fundCode) {
        String url = String.format(ESTAB_DATE_URL, fundCode);
        String json = httpClient.getText(url, HEADERS);
        if (json == null) {
            log.warn("获取基金 {} 成立日期失败（重试耗尽）", fundCode);
            return null;
        }
        Matcher m = ESTABDATE_PATTERN.matcher(json);
        if (m.find()) {
            return LocalDate.parse(m.group(1), DATE_FMT);
        }
        // 失败日志打印响应体截断，便于区分"反爬拦截"与"字段缺失"
        log.warn("基金 {} 成立日期响应异常 响应={}",
                fundCode, HttpClientWrapper.truncate(json, 300));
        return null;
    }
}
