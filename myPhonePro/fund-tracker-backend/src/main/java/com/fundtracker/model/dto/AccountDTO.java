package com.fundtracker.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** 账户视图：账户信息 + 该账户下的资产 + 折算人民币的合计 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDTO {
    private String id;
    private String name;
    /** daily / invest / exchange / other */
    private String purpose;
    /** CNY / USDT */
    private String baseCurrency;
    /** 显式声明：Lombok isDefault() 会被 Jackson 序列化成 "default"，前端读不到 */
    @JsonProperty("isDefault")
    private boolean isDefault;
    private int sortOrder;
    /** 账户总资产（已折算人民币） */
    private BigDecimal totalValueCny;
    private int assetCount;
    private List<ManualAssetDTO> assets;
}
