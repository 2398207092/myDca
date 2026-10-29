package com.fundtracker.service;

import com.fundtracker.model.dto.ExchangeRateDTO;
import com.fundtracker.model.dto.RefreshCryptoPricesResp;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.enums.CostAlgorithm;
import com.fundtracker.model.enums.HoldingType;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.repository.ManualAssetRepository;
import com.fundtracker.service.provider.CryptoPriceProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * CryptoPriceService 单元测试。
 * 覆盖：多源降级、汇率折算、全源失败、无持仓、漏刷币种。
 */
@ExtendWith(MockitoExtension.class)
class CryptoPriceServiceTest {

    @Mock
    private HoldingRepository holdingRepository;
    @Mock
    private ExchangeRateService exchangeRateService;
    @Mock
    private ManualAssetRepository manualAssetRepository;
    @Mock
    private CryptoPriceProvider okx;
    @Mock
    private CryptoPriceProvider gate;

    private CryptoPriceService service;

    @BeforeEach
    void setUp() {
        // OKX 优先，Gate 兜底（与 @Order 一致）
        service = new CryptoPriceService(List.of(okx, gate), holdingRepository, exchangeRateService, manualAssetRepository);
        // name() 只在遍历 provider 时调用，某些场景（首个源就成功）不会用到 gate.name()
        lenient().when(okx.name()).thenReturn("okx");
        lenient().when(gate.name()).thenReturn("gate");
    }

    @Test
    @DisplayName("OKX 失败时降级 Gate，并乘汇率折算人民币")
    void fallbackToGateAndConvertByRate() {
        when(okx.fetchUsdtPrice(anyString())).thenReturn(Optional.empty());
        when(gate.fetchUsdtPrice(anyString())).thenAnswer(inv -> {
            String symbol = inv.getArgument(0);
            if ("BTC".equals(symbol)) return Optional.of(new BigDecimal("80000"));
            if ("ETH".equals(symbol)) return Optional.of(new BigDecimal("3000"));
            return Optional.empty();
        });
        when(exchangeRateService.listAll()).thenReturn(List.of(rate("USD/CNY", "6.7")));

        Holding btc = holding("BTC", "0.01", "USDT");
        when(holdingRepository.findByUserIdAndQuoteCurrencyAndDeletedFalse("u1", "USDT"))
                .thenReturn(List.of(btc));

        RefreshCryptoPricesResp resp = service.refresh("u1");

        assertFalse(resp.isStale(), "至少一个源成功，不应标记 stale");
        assertEquals(1, resp.getUpdatedHoldings(), "本次只应更新 BTC 一条（ETH 没有持仓）");
        // 0.01 × 80000 × 6.7 = 5360.00
        assertEquals(0, new BigDecimal("5360.00").compareTo(btc.getMarketValue()), "市值应为份额×USDT价×汇率");
        assertEquals(0, new BigDecimal("536000.00").compareTo(btc.getLatestPrice()), "人民币单价 = USDT价 × 汇率");
        assertEquals("gate", btc.getPriceSource(), "来源应为兜底的 gate");
        assertNotNull(btc.getPriceUpdatedAt());
    }

    @Test
    @DisplayName("所有数据源失败时标记 stale，不覆盖已有价格")
    void allProvidersFailKeepsOldPrice() {
        when(okx.fetchUsdtPrice(anyString())).thenReturn(Optional.empty());
        when(gate.fetchUsdtPrice(anyString())).thenReturn(Optional.empty());
        when(exchangeRateService.listAll()).thenReturn(List.of(rate("USD/CNY", "6.7")));

        Holding btc = holding("BTC", "0.01", "USDT");
        btc.setLatestPrice(new BigDecimal("500000"));
        btc.setQuotePrice(new BigDecimal("75000"));
        btc.setMarketValue(new BigDecimal("5000"));
        when(holdingRepository.findByUserIdAndQuoteCurrencyAndDeletedFalse("u1", "USDT"))
                .thenReturn(List.of(btc));

        RefreshCryptoPricesResp resp = service.refresh("u1");

        assertTrue(resp.isStale(), "全部数据源失败必须标记 stale，不能静默用旧值");
        assertEquals(0, resp.getUpdatedHoldings());
        assertEquals(0, new BigDecimal("5000").compareTo(btc.getMarketValue()), "旧市值不应被改写");
        assertTrue(resp.getMessage().contains("不可用") || resp.getMessage().contains("冷却"),
                "提示应说明数据源不可用，实际：" + resp.getMessage());
    }

    @Test
    @DisplayName("没有 USDT 持仓时不发网络请求、返回 0 更新")
    void noHoldings() {
        when(holdingRepository.findByUserIdAndQuoteCurrencyAndDeletedFalse("u1", "USDT"))
                .thenReturn(List.of());

        RefreshCryptoPricesResp resp = service.refresh("u1");

        assertEquals(0, resp.getUpdatedHoldings());
        assertTrue(resp.getPrices().isEmpty());
        // 2026-10-29：无持仓时直接返回，不再抓 BTC/ETH（请求数减半、冷启动更快）
        org.mockito.Mockito.verifyNoInteractions(gate, okx);
    }

    @Test
    @DisplayName("按 userId 圈定持仓：接口传 userId 时不查全局")
    void scopedByUserId() {
        when(okx.fetchUsdtPrice(anyString())).thenReturn(Optional.of(new BigDecimal("80000")));
        when(exchangeRateService.listAll()).thenReturn(List.of(rate("USD/CNY", "6.7")));
        when(holdingRepository.findByUserIdAndQuoteCurrencyAndDeletedFalse("u1", "USDT"))
                .thenReturn(List.of(holding("BTC", "0.01", "USDT")));

        service.refresh("u1");

        // 必须走带 userId 的查询（跨用户写库是 P0 级问题，这里守住入口）
        org.mockito.Mockito.verify(holdingRepository, org.mockito.Mockito.atLeastOnce())
                .findByUserIdAndQuoteCurrencyAndDeletedFalse("u1", "USDT");
    }

    private ExchangeRateDTO rate(String pair, String value) {
        return ExchangeRateDTO.builder()
                .pair(pair)
                .label(pair)
                .rate(new BigDecimal(value))
                .updatedAt("2026-10-27 22:00:00")
                .build();
    }

    private Holding holding(String code, String shares, String quoteCurrency) {
        BigDecimal shareValue = new BigDecimal(shares);
        BigDecimal cost = new BigDecimal("100");
        return Holding.builder()
                .id(UUID.randomUUID().toString())
                .name(code)
                .code(code)
                .type(HoldingType.crypto)
                .costAlgorithm(CostAlgorithm.diluted)
                .shares(shareValue)
                .costPerShare(new BigDecimal("10000"))
                .cost(cost)
                .marketValue(cost)
                .predictedDividend(BigDecimal.ZERO)
                .dividendRate(BigDecimal.ZERO)
                .priceDividendRate(BigDecimal.ZERO)
                .totalDividendReceived(BigDecimal.ZERO)
                .netInvestment(cost)
                .dividendRecoveryRate(BigDecimal.ZERO)
                .estimatedRecoveryYears(BigDecimal.ZERO)
                .reinvestRecoveryYears(BigDecimal.ZERO)
                .color("#f7931a")
                .quoteCurrency(quoteCurrency)
                .deleted(false)
                .build();
    }
}
