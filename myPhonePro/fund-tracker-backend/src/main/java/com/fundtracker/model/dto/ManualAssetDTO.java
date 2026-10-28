package com.fundtracker.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualAssetDTO {
    private String id;
    private String name;
    private String type; // crypto / cash
    private BigDecimal amount;

    /** 归属账户 */
    private String accountId;
    /** CNY / USDT / BTC / ETH */
    private String assetCode;
    /** 数量：加密币持仓量，现金为 null */
    private BigDecimal quantity;
    /** 单价（人民币，USDT 余额=汇率、币种=USDT价×汇率）：加密币币价，现金为 null */
    private BigDecimal unitPrice;
    private String priceSource;
    private LocalDateTime priceUpdatedAt;

    private String currency;
    private String note;
    /** 显式声明：Lombok isPrimary() 会被 Jackson 序列化成 "primary" */
    @JsonProperty("isPrimary")
    private boolean isPrimary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
