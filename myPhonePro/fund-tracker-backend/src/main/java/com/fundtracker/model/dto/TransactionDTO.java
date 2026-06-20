package com.fundtracker.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDTO {
    private String id;
    private String holdingId;
    private String type;
    private String date;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal fee;
    private BigDecimal total;
    private String source;
    private String dcaPlanId;
}
