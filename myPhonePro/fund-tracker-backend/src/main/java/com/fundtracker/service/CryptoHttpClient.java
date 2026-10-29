package com.fundtracker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * 加密行情专用 HTTP 客户端。
 *
 * <p>与全局 {@code HttpClientWrapper}（Jsoup，每次新建连接）的区别：
 * <ul>
 *   <li><b>连接复用</b>：单例 HttpClient 内置连接池 / keep-alive，冷启动握手只付一次
 *       （生产到 gate 的 TLS 握手约 15s，复用后 &lt;1s）</li>
 *   <li><b>超时更短</b>：4s（行情刷新是交互式操作，宁可失败沿用上次价格，不让用户干等）</li>
 *   <li><b>不重试</b>：由调用方（数据源降级）决定是否换源，避免 10s×3 的叠加等待</li>
 * </ul>
 *
 * <p>2026-10-29 实测：本地冷启动刷新 32.2s → 后续 0.2s；生产 60s。
 * 根因是数据源超时 10s 重试 3 次 + Jsoup 无连接复用。
 */
@Slf4j
@Component
public class CryptoHttpClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(4);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(4);

    /** 单例客户端：连接池 + keep-alive 由此生效，不要每次 new */
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * GET 请求，返回响应体；失败（超时 / 非 200 / IO 异常）返回 empty，由调用方降级到下一个源。
     */
    public Optional<String> getText(String url, Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .GET();
            if (headers != null) {
                headers.forEach(builder::header);
            }
            HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("行情请求非 200：{} → {}", url, resp.statusCode());
                return Optional.empty();
            }
            return Optional.ofNullable(resp.body());
        } catch (Exception e) {
            log.warn("行情请求失败：{} err={}", url, e.getClass().getSimpleName() + ":" + e.getMessage());
            return Optional.empty();
        }
    }
}
