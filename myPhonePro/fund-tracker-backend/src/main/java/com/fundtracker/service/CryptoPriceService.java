package com.fundtracker.service;

import com.fundtracker.model.dto.CryptoPriceDTO;
import com.fundtracker.model.dto.ExchangeRateDTO;
import com.fundtracker.model.dto.RefreshCryptoPricesResp;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.service.provider.CryptoPriceProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 加密币价格刷新。
 *
 * 设计要点：
 * 1. 多源降级：按 @Order 依次尝试（OKX → Gate），第一个成功即止。
 * 2. 失败缓存：某个源失败后 10 分钟内跳过，避免每次都等它超时。
 * 3. latestPrice 始终是人民币价，quotePrice 保留 USDT 原价，现有市值计算逻辑不受影响。
 * 4. 全部失败时不静默使用旧值，返回 stale=true 让前端显式提示。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CryptoPriceService {

    /** 当前支持的币种。与前端「添加标的」的快捷入口保持一致 */
    private static final List<String> SYMBOLS = List.of("BTC", "ETH");
    private static final Duration SKIP_FAILED_FOR = Duration.ofMinutes(10);
    private static final BigDecimal DEFAULT_USD_CNY = new BigDecimal("7.2");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final List<CryptoPriceProvider> providers;
    private final HoldingRepository holdingRepository;
    private final ExchangeRateService exchangeRateService;

    /** 数据源名 → 最近失败时间 */
    private final Map<String, LocalDateTime> failedAt = new ConcurrentHashMap<>();

    /**
     * 刷新价格并写回持仓。
     * @param userId 只刷新该用户的持仓；传 null 表示刷新全部用户（仅供定时任务）
     */
    public RefreshCryptoPricesResp refresh(String userId) {
        Map<String, BigDecimal> fetched = new LinkedHashMap<>();
        Set<String> failedSources = new LinkedHashSet<>();
        String source = null;

        for (String symbol : SYMBOLS) {
            for (CryptoPriceProvider provider : providers) {
                if (isRecentlyFailed(provider.name())) {
                    continue;
                }
                Optional<BigDecimal> price = provider.fetchUsdtPrice(symbol);
                if (price.isPresent()) {
                    fetched.put(symbol, price.get());
                    source = provider.name();
                    failedAt.remove(provider.name());
                    log.info("{} 价格获取成功：{} USDT（源={}）", symbol, price.get(), provider.name());
                    break;
                }
                failedAt.put(provider.name(), LocalDateTime.now());
                failedSources.add(provider.name());
                log.warn("{} 价格获取失败：源={}，尝试下一个", symbol, provider.name());
            }
        }

        BigDecimal usdCny = usdCnyRate();
        int updated = fetched.isEmpty() ? 0 : applyToHoldings(fetched, usdCny, source, userId);

        List<CryptoPriceDTO> prices = currentPrices(userId);

        boolean stale = fetched.isEmpty();
        String message;
        if (stale) {
            message = failedSources.isEmpty()
                    ? "本次未发起请求（数据源均在冷却期），显示上次价格"
                    : "所有数据源不可用（" + String.join("、", failedSources) + "），沿用上次价格";
        } else {
            message = "已更新 " + updated + " 个持仓（" + source + "）";
        }

        return RefreshCryptoPricesResp.builder()
                .prices(prices)
                .updatedHoldings(updated)
                .refreshedAt(LocalDateTime.now().format(TS))
                .stale(stale)
                .message(message)
                .build();
    }

    /** 只读取当前持仓上的价格，不发请求 */
    public List<CryptoPriceDTO> currentPrices(String userId) {
        List<CryptoPriceDTO> list = new ArrayList<>();
        for (Holding h : listTargets(userId)) {
            list.add(CryptoPriceDTO.builder()
                    .symbol(h.getCode())
                    .usdtPrice(h.getQuotePrice())
                    .cnyPrice(h.getLatestPrice())
                    .source(h.getPriceSource())
                    .updatedAt(h.getPriceUpdatedAt())
                    .build());
        }
        return list;
    }

    /** 按用户圈定要处理的持仓；userId 为 null 时返回全部（仅供定时任务） */
    private List<Holding> listTargets(String userId) {
        return userId == null
                ? holdingRepository.findByQuoteCurrencyAndDeletedFalse("USDT")
                : holdingRepository.findByUserIdAndQuoteCurrencyAndDeletedFalse(userId, "USDT");
    }

    private boolean isRecentlyFailed(String providerName) {
        LocalDateTime at = failedAt.get(providerName);
        return at != null && at.plus(SKIP_FAILED_FOR).isAfter(LocalDateTime.now());
    }

    private int applyToHoldings(Map<String, BigDecimal> usdtPrices, BigDecimal usdCny, String source, String userId) {
        int updated = 0;
        for (Holding h : listTargets(userId)) {
            if (h.getCode() == null) continue;
            BigDecimal usdt = usdtPrices.get(h.getCode().toUpperCase());
            if (usdt == null) continue;

            BigDecimal cnyPrice = usdt.multiply(usdCny).setScale(4, RoundingMode.HALF_UP);
            h.setQuotePrice(usdt);
            h.setLatestPrice(cnyPrice);
            h.setMarketValue(h.getShares().multiply(cnyPrice).setScale(2, RoundingMode.HALF_UP));
            h.setPriceDate(LocalDate.now());
            h.setPriceSource(source);
            h.setPriceUpdatedAt(LocalDateTime.now());
            holdingRepository.save(h);
            updated++;
            log.info("更新持仓 {}：{} {} × {} CNY = {}",
                    h.getName(), h.getShares(), h.getCode(), cnyPrice, h.getMarketValue());
        }
        return updated;
    }

    /** USD/CNY 汇率；拿不到时用兜底值并记 warning（不静默） */
    private BigDecimal usdCnyRate() {
        try {
            return exchangeRateService.listAll().stream()
                    .filter(r -> "USD/CNY".equals(r.getPair()))
                    .map(ExchangeRateDTO::getRate)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElseGet(() -> {
                        log.warn("汇率表缺少 USD/CNY，使用兜底值 {}", DEFAULT_USD_CNY);
                        return DEFAULT_USD_CNY;
                    });
        } catch (Exception e) {
            log.warn("汇率获取异常，使用兜底值 {}：{}", DEFAULT_USD_CNY, e.getMessage());
            return DEFAULT_USD_CNY;
        }
    }
}
