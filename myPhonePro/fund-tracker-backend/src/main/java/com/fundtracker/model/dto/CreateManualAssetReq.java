package com.fundtracker.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateManualAssetReq {
    @NotBlank(message = "名称不能为空")
    private String name;

    @NotBlank(message = "类型不能为空")
    private String type; // crypto / cash

    @NotNull(message = "金额不能为空")
    @Positive(message = "金额必须大于 0")
    private BigDecimal amount;

    private String currency; // CNY / 默认 CNY

    /** 归属账户；为空时挂到默认账户 */
    private String accountId;

    /** CNY / USDT / BTC / ETH；为空时按 type 推断（cash→CNY，crypto→BTC） */
    private String assetCode;

    /** 加密币数量；现金留空 */
    private BigDecimal quantity;

    /** 单价（USD）；现金留空 */
    private BigDecimal unitPrice;

    private String priceSource;

    private String note;
}
