package com.fundtracker.service.provider;

import com.fundtracker.common.HttpClientWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 天天基金净值数据源 Provider（#1 外部数据源层）
 *
 * 内聚：URL、UA（浏览器 UA）、重试、失败策略。
 * 抓取天天基金 pingzhongdata/{code}.js 原始内容，解析/增量逻辑由业务 Service 完成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NavDataProvider {

    private final HttpClientWrapper httpClient;

    /** 天天基金净值数据接口（必须 HTTPS，防止中间人篡改） */
    private static final String PINGZHONG_DATA_URL = "https://fund.eastmoney.com/pingzhongdata/%s.js";

    /** 净值 JS 接口 UA：HTML/JS 页面需浏览器 UA（与 fhsp 分红页一致，与成立日期接口相反） */
    private static final Map<String, String> HEADERS = Map.of(
            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

    /**
     * 抓取净值 JS 原始内容（含重试）
     *
     * @return JS 内容；重试耗尽返回 null
     */
    public String fetchRawJs(String fundCode) {
        String url = String.format(PINGZHONG_DATA_URL, fundCode);
        String body = httpClient.getText(url, HEADERS);
        if (body == null) {
            log.warn("基金 {} 净值抓取重试 3 次后仍失败（可能为过期数据）", fundCode);
        }
        return body;
    }
}
