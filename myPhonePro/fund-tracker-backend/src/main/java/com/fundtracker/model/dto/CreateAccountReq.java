package com.fundtracker.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAccountReq {

    @NotBlank(message = "账户名称不能为空")
    private String name;

    /** daily / invest / exchange / other，默认 other */
    private String purpose;

    /** CNY / USDT，默认 CNY */
    private String baseCurrency;

    private Integer sortOrder;
}
