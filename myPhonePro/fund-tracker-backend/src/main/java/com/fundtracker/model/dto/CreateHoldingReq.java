package com.fundtracker.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateHoldingReq {
    @NotBlank(message = "名称不能为空")
    private String name;

    @NotBlank(message = "代码不能为空")
    private String code;

    @NotBlank(message = "类型不能为空")
    private String type;

    private String costAlgorithm;  // 默认 'diluted'

    @Positive(message = "持有份额必须大于 0")
    private BigDecimal shares;

    @Positive(message = "成本必须大于 0")
    private BigDecimal cost;

    private String assetCategory; // us_stock / gold / dividend / null=未分类
}
