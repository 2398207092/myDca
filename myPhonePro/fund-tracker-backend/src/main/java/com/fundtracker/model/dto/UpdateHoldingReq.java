package com.fundtracker.model.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateHoldingReq {
    private String name;
    private String costAlgorithm;
    private BigDecimal shares;
    private BigDecimal costPerShare;
    private String assetCategory;
    private BigDecimal buyFeeRate;
    private BigDecimal sellFeeRate;
    /** 归属账户：空串 = 清除归属；USDT 账户只能挂加密资产 */
    private String accountId;
}
