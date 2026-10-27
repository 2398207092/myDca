package com.fundtracker.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 刷新加密币价格的结果 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshCryptoPricesResp {
    private List<CryptoPriceDTO> prices;
    /** 本次更新的持仓数量 */
    private int updatedHoldings;
    private String refreshedAt;
    /** true = 所有数据源均失败，返回的是旧价 */
    private boolean stale;
    private String message;
}
