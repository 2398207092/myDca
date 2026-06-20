package com.fundtracker.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateTransactionReq {
    @NotBlank
    private String holdingId;

    @NotBlank
    private String type;

    @NotBlank
    private String date;

    @Positive
    private BigDecimal quantity;

    @Positive
    private BigDecimal price;

    private BigDecimal fee;  // 可选，默认 0

    private String source;  // 可选，默认 "manual"
    private String dcaPlanId;  // 可选
}
