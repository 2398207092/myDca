package com.fundtracker.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 单个币种的价格视图 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CryptoPriceDTO {
    private String symbol;
    /** USDT 计价（≈USD） */
    private BigDecimal usdtPrice;
    /** 折算后的人民币价格 */
    private BigDecimal cnyPrice;
    /** 数据源：okx / gate */
    private String source;
    private LocalDateTime updatedAt;
}
