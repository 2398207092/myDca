package com.fundtracker.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateBudgetCategoryReq {
    private String name;
    private BigDecimal amount;
    private Integer sortOrder;
}
