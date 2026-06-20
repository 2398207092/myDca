package com.fundtracker.model.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateCoverageCategoryReq {
    private String name;
    private String icon;
    private BigDecimal percentage;
    private String color;
}
