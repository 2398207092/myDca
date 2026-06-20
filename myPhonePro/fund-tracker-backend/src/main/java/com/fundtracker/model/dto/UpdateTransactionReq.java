package com.fundtracker.model.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateTransactionReq {
    private String type;
    private String date;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal fee;
}
