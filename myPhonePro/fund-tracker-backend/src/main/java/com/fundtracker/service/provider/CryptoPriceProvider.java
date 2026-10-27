package com.fundtracker.service.provider;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 加密币价格源。
 *
 * 约定：返回的是 USDT（≈USD）计价的价格，不含汇率折算。
 * 人民币价由 CryptoPriceService 统一乘 USD/CNY 得到，避免每个源各算一遍。
 */
public interface CryptoPriceProvider {

    /** 源名称，写入 holdings.price_source */
    String name();

    /**
     * 拉取某币种相对 USDT 的最新价。
     * @param symbol 币种代码，如 BTC
     * @return 价格；失败返回 empty（不抛异常，由调用方决定降级）
     */
    Optional<BigDecimal> fetchUsdtPrice(String symbol);
}
