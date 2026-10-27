package com.fundtracker.model.entity;

import com.fundtracker.model.enums.CostAlgorithm;
import com.fundtracker.model.enums.HoldingType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "holdings", indexes = {
    @Index(name = "idx_holdings_deleted", columnList = "deleted")
})
public class Holding {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** 数据归属用户 ID */
    @Column(length = 36)
    private String userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HoldingType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CostAlgorithm costAlgorithm;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal shares;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal costPerShare;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal cost;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal marketValue;

    @Column(precision = 18, scale = 4)
    private BigDecimal latestPrice;

    private LocalDate priceDate;

    /** 原始报价币种：CNY（基金/ETF）或 USDT（加密币）。latestPrice 始终是折算后的人民币价 */
    @Column(name = "quote_currency", nullable = false, length = 10)
    @Builder.Default
    private String quoteCurrency = "CNY";

    /** 原始报价（如 79827.4 USDT）；CNY 标的为空 */
    @Column(name = "quote_price", precision = 24, scale = 8)
    private BigDecimal quotePrice;

    /** 成本是否为估算值（如从冷钱包转入，真实成本不可考，按建仓价估算） */
    @Column(name = "cost_estimated", nullable = false)
    @Builder.Default
    private boolean costEstimated = false;

    /** 价格来源：okx / gate / manual */
    @Column(name = "price_source", length = 20)
    private String priceSource;

    @Column(name = "price_updated_at")
    private LocalDateTime priceUpdatedAt;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal predictedDividend;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal dividendRate;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal priceDividendRate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal totalDividendReceived;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal netInvestment;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal dividendRecoveryRate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal estimatedRecoveryYears;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal reinvestRecoveryYears;

    @Column(nullable = false, length = 10)
    private String color;

    @Column(nullable = false)
    private boolean deleted;

    @Column(length = 20)
    private String assetCategory; // us_stock / gold / dividend / null=未分类

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private Boolean dividendReinvest = false;

    /** 买入手续费率（百分数值，如 0.15 表示 0.15%）。新建标的默认 0.15，可改 */
    @Column(nullable = false, precision = 8, scale = 4)
    @Builder.Default
    private BigDecimal buyFeeRate = new BigDecimal("0.15");

    /** 卖出手续费率（百分数值，如 0 表示无赎回费）。默认 0，可改 */
    @Column(nullable = false, precision = 8, scale = 4)
    @Builder.Default
    private BigDecimal sellFeeRate = BigDecimal.ZERO;
}
