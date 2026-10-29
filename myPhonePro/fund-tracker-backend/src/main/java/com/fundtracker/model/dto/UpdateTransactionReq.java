package com.fundtracker.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateTransactionReq {
    private String type;
    private String date;
    @Positive(message = "数量必须大于 0")
    private BigDecimal quantity;
    @Positive(message = "价格必须大于 0")
    private BigDecimal price;
    @DecimalMin(value = "0", message = "手续费不能为负")
    private BigDecimal fee;
}
