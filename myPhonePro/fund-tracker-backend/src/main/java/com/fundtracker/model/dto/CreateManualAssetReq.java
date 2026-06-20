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

    private String note;
}
