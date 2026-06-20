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
public class LiveExpenseDTO {
    private String id;
    private String name;
    private String icon;
    private BigDecimal monthlyAmount;
    private Integer sortOrder;
}
