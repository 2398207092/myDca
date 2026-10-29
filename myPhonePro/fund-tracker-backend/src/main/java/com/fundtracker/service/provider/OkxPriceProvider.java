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
 * OKX 行情源。用户主用交易所。
 *
 * 注意：本机实测 www.okx.com 被 DNS 污染（解析到 169.254.0.2），请求会超时；
 * 服务器网络可能正常。因此本类失败后由 CryptoPriceService 降级到 Gate。
 */
@Slf4j
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.crypto.okx-enabled", havingValue = "true")
@Order(1)
@RequiredArgsConstructor
public class OkxPriceProvider implements CryptoPriceProvider {

    private static final String URL = "https://www.okx.com/api/v5/market/ticker?instId=%s-USDT";
    private static final Map<String, String> HEADERS = Map.of(
            "User-Agent", "Mozilla/5.0",
            "Accept", "application/json"
    );

    private final com.fundtracker.service.CryptoHttpClient http;
    private final ObjectMapper mapper;

    @Override
    public String name() {
        return "okx";
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
            if (!"0".equals(root.path("code").asText())) {
                log.warn("OKX 返回异常 code={} msg={}", root.path("code").asText(), root.path("msg").asText());
                return Optional.empty();
            }
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                return Optional.empty();
            }
            return parse(data.get(0).path("last").asText());
        } catch (Exception e) {
            log.warn("OKX 解析失败: {}", e.getMessage());
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
