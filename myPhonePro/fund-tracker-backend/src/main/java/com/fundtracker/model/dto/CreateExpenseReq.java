package com.fundtracker.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateExpenseReq {
    @NotBlank(message = "支出名称不能为空")
    private String name;

    @NotBlank(message = "图标不能为空")
    private String icon;

    @NotNull(message = "月度金额不能为空")
    @Positive(message = "金额必须大于0")
    private BigDecimal monthlyAmount;

    private Integer sortOrder;
}
