package com.fundtracker.common;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * 统一 HTTP 客户端封装（#18 三种 HTTP 客户端混用治理）
 *
 * 统一能力：
 * - getText / getDocument 两个入口，覆盖 JSON 文本与 HTML 解析两类场景
 * - 超时 10s、重试 3 次（指数退避 500ms → 1s → 2s）
 * - 每次调用显式传入 headers（不隐式复用"项目通用 UA"）
 *
 * 注意：不同外部接口的反爬策略不同（如天天基金 FundMNBasicInformation 接口
 * 只放行 curl/ UA、拦截浏览器 UA；fhsp 分红页则相反），因此调用方必须为
 * 每个接口单独验证并指定 UA/Header，不能假设统一 UA 可用。
 */
@Slf4j
@Component
public class HttpClientWrapper {

    private static final int TIMEOUT_MS = 10000;

    private static final RetryTemplate HTTP_RETRY = RetryTemplate.builder()
            .maxAttempts(3)
            .exponentialBackoff(500, 2, 5000)
            .retryOn(IOException.class)
            .build();

    /**
     * GET 请求，返回响应体文本（JSON/纯文本场景）
     *
     * @param url     完整 URL
     * @param headers 请求头（调用方必须按接口实测指定，如 UA）
     * @return 响应体；重试耗尽返回 null
     */
    public String getText(String url, Map<String, String> headers) {
        try {
            return HTTP_RETRY.execute(context -> Jsoup.connect(url)
                    .headers(headers == null ? Collections.emptyMap() : headers)
                    .ignoreContentType(true)
                    .timeout(TIMEOUT_MS)
                    .execute()
                    .body());
        } catch (Exception e) {
            log.warn("HTTP GET 失败（重试耗尽）url={} err={}", url, e.getMessage());
            return null;
        }
    }

    /**
     * GET 请求，返回解析后的 HTML 文档（HTML 解析场景）
     *
     * @param url     完整 URL
     * @param headers 请求头（调用方必须按接口实测指定，如 UA）
     * @return HTML 文档；重试耗尽返回 null
     */
    public Document getDocument(String url, Map<String, String> headers) {
        try {
            return HTTP_RETRY.execute(context -> Jsoup.connect(url)
                    .headers(headers == null ? Collections.emptyMap() : headers)
                    .timeout(TIMEOUT_MS)
                    .get());
        } catch (Exception e) {
            log.warn("HTTP GET 失败（重试耗尽）url={} err={}", url, e.getMessage());
            return null;
        }
    }

    /** 截断长文本用于日志（避免刷屏） */
    public static String truncate(String text, int maxLen) {
        if (text == null) return "null";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
