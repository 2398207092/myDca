package com.fundtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.ExchangeRateDTO;
import com.fundtracker.model.dto.RefreshExchangeRatesResp;
import com.fundtracker.model.entity.ExchangeRate;
import com.fundtracker.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateRepository repository;

    // 记录上次刷新时间
    private LocalDateTime lastRefreshTime = LocalDateTime.MIN;

    // Frankfurter API（基于欧洲央行数据，免费，无需 API Key）
    private static final String API_BASE = "https://api.frankfurter.app/latest";

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public List<ExchangeRateDTO> listAll() {
        List<ExchangeRate> rates = repository.findAll();
        if (rates.isEmpty()) {
            // 数据库为空时，尝试联网获取，失败则用硬编码兜底
            List<ExchangeRate> fetched = fetchFromApi();
            if (!fetched.isEmpty()) {
                repository.saveAll(fetched);
                rates = fetched;
            } else {
                return initDefaults();
            }
        }
        return rates.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public RefreshExchangeRatesResp refresh() {
        // 30 秒限流检查
        if (lastRefreshTime != null &&
                lastRefreshTime.plusSeconds(30).isAfter(LocalDateTime.now())) {
            throw BusinessException.rateLimitExceeded();
        }

        lastRefreshTime = LocalDateTime.now();
        String refreshedAt = lastRefreshTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 从 API 拉取实时汇率
        List<ExchangeRate> apiRates = fetchFromApi();

        if (!apiRates.isEmpty()) {
            // API 成功，更新数据库
            for (ExchangeRate newRate : apiRates) {
                Optional<ExchangeRate> existing = repository.findByPair(newRate.getPair());
                if (existing.isPresent()) {
                    ExchangeRate old = existing.get();
                    old.setRate(newRate.getRate());
                    old.setUpdatedAt(LocalDateTime.now());
                    repository.save(old);
                } else {
                    newRate.setUpdatedAt(LocalDateTime.now());
                    repository.save(newRate);
                }
            }
            // 删除不再支持的货币对（如有）
            // 保留当前已有的
        } else {
            // API 失败，使用已有数据库值（不修改）
            log.warn("刷新汇率失败，API 不可达，使用本地缓存值");
        }

        List<ExchangeRate> rates = repository.findAll();
        return RefreshExchangeRatesResp.builder()
                .rates(rates.stream().map(this::toDTO).collect(Collectors.toList()))
                .refreshedAt(refreshedAt)
                .build();
    }

    /**
     * 从 Frankfurter API 获取实时汇率
     * 返回 {"amount":1.0,"base":"USD","date":"2026-06-17","rates":{"CNY":7.2523}}
     */
    List<ExchangeRate> fetchFromApi() {
        try {
            ExchangeRate usdRate = fetchSinglePair("USD", "CNY", "美金/人民币");
            ExchangeRate hkdRate = fetchSinglePair("HKD", "CNY", "港币/人民币");

            if (usdRate != null && hkdRate != null) {
                log.info("汇率API获取成功: USD/CNY={}, HKD/CNY={}", usdRate.getRate(), hkdRate.getRate());
                return List.of(usdRate, hkdRate);
            }
            if (usdRate != null) return List.of(usdRate);
            if (hkdRate != null) return List.of(hkdRate);

            log.warn("汇率API: 未能获取任何有效汇率");
            return List.of();
        } catch (Exception e) {
            log.warn("汇率API请求失败: {}", e.getMessage());
            return List.of();
        }
    }

    private ExchangeRate fetchSinglePair(String from, String to, String label) {
        try {
            String url = API_BASE + "?from=" + from + "&to=" + to;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("汇率API返回非200状态: {} {}", from, response.statusCode());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode ratesNode = root.get("rates");

            if (ratesNode == null || !ratesNode.has(to)) {
                log.warn("汇率API响应中无目标货币: {}", response.body());
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

    private List<ExchangeRateDTO> initDefaults() {
        ExchangeRate hkd = ExchangeRate.builder()
                .id(UUID.randomUUID().toString())
                .pair("HKD/CNY")
                .label("港币/人民币")
                .rate(new BigDecimal("0.9245"))
                .updatedAt(LocalDateTime.now())
                .build();

        ExchangeRate usd = ExchangeRate.builder()
                .id(UUID.randomUUID().toString())
                .pair("USD/CNY")
                .label("美金/人民币")
                .rate(new BigDecimal("7.2341"))
                .updatedAt(LocalDateTime.now())
                .build();

        repository.save(hkd);
        repository.save(usd);

        return repository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    private ExchangeRateDTO toDTO(ExchangeRate rate) {
        return ExchangeRateDTO.builder()
                .pair(rate.getPair())
                .label(rate.getLabel())
                .rate(rate.getRate())
                .updatedAt(rate.getUpdatedAt()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .build();
    }
}
