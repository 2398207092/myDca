package com.fundtracker.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundtracker.common.HttpClientWrapper;
import com.fundtracker.model.entity.ExchangeRate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 汇率数据源 Provider（#1 外部数据源层）
 *
 * 内聚：URL、请求头、JSON 解析、失败策略。
 * 数据源：Frankfurter API（基于欧洲央行数据，免费，无需 API Key）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeRateProvider {

    private final HttpClientWrapper httpClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Frankfurter API（基于欧洲央行数据，免费，无需 API Key） */
    private static final String API_BASE = "https://api.frankfurter.app/latest";

    /** Frankfurter API 请求头（无需特殊 UA，默认即可） */
    private static final Map<String, String> HEADERS = Map.of(
            "User-Agent", "fund-tracker/1.0");

    /**
     * 获取单对货币汇率
     *
     * @return 汇率记录；获取失败返回 null
     */
    public ExchangeRate fetchSinglePair(String from, String to, String label) {
        try {
            String url = API_BASE + "?from=" + from + "&to=" + to;
            String body = httpClient.getText(url, HEADERS);
            if (body == null) {
                log.warn("汇率API请求失败（重试耗尽）: {}/{}", from, to);
                return null;
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode ratesNode = root.get("rates");

            if (ratesNode == null || !ratesNode.has(to)) {
                log.warn("汇率API响应中无目标货币: {}", HttpClientWrapper.truncate(body, 200));
                return null;
            }

            BigDecimal rate = new BigDecimal(ratesNode.get(to).asText());

            return ExchangeRate.builder()
                    .id(UUID.randomUUID().toString())
                    .pair(from + "/" + to)
                    .label(label)
                    .rate(rate)
                    .updatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.warn("获取汇率 {}/{} 失败: {}", from, to, e.getMessage());
            return null;
        }
    }
}
