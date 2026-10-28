package com.fundtracker.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateDcaPlanReq {
    @NotBlank
    private String holdingId;

    /** 扣款账户（可空）：执行时从该账户扣现金/USDT；空 = 纯记账 */
    private String accountId;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotBlank
    private String frequency; // daily / weekly / biweekly / monthly

    private Integer day; // 非daily时使用
}
