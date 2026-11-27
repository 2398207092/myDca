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
public class BudgetRecordDTO {
    private String id;
    private String categoryId;
    private String categoryName;
    /** yyyy-MM-dd */
    private String occurDate;
    private BigDecimal amount;
    private String note;
}
