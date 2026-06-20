package com.fundtracker.model.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateExpenseReq {
    private String name;
    private String icon;
    private BigDecimal monthlyAmount;
    private Integer sortOrder;
}
