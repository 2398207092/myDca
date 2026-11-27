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
public class BudgetCategoryDTO {
    private String id;
    private String name;
    /** 年度额度 */
    private BigDecimal amount;
    /** 该类型今年已用 */
    private BigDecimal spent;
    /** 剩余额度（可为负＝超支） */
    private BigDecimal remaining;
    /** 该类型今年的记录笔数 */
    private long recordCount;
    private Integer sortOrder;
}
