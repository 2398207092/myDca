package com.fundtracker.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundtracker.common.HttpClientWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Gate.io 行情源，作为 OKX 的兜底。
 *
 * 2026-10-27 本机实测可直连（HTTP 200），返回：
 * [{"currency_pair":"BTC_USDT","last":"79827.4",...}]
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class GatePriceProvider implements CryptoPriceProvider {

    private static final String URL = "https://api.gateio.ws/api/v4/spot/tickers?currency_pair=%s_USDT";
    private static final Map<String, String> HEADERS = Map.of(
            "User-Agent", "Mozilla/5.0",
            "Accept", "application/json"
    );

    private final com.fundtracker.service.CryptoHttpClient http;
    private final ObjectMapper mapper;

    @Override
    public String name() {
        return "gate";
    }

    @Override
    public Optional<BigDecimal> fetchUsdtPrice(String symbol) {
        String url = URL.formatted(symbol.toUpperCase());
        java.util.Optional<String> resp = http.getText(url, HEADERS);
        if (resp.isEmpty() || resp.get().isBlank()) {
            return Optional.empty();
        }
        String body = resp.get();
        try {
            JsonNode root = mapper.readTree(body);
            if (!root.isArray() || root.isEmpty()) {
                return Optional.empty();
            }
            return parse(root.get(0).path("last").asText());
        } catch (Exception e) {
            log.warn("Gate 解析失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<BigDecimal> parse(String raw) {
        try {
            BigDecimal v = new BigDecimal(raw.trim());
            return v.signum() > 0 ? Optional.of(v) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
