package com.fundtracker.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetOverviewDTO {
    private Integer year;
    /** 年度额度合计 */
    private BigDecimal totalAmount;
    /** 今年已用合计 */
    private BigDecimal totalSpent;
    /** 剩余额度（可为负＝超支） */
    private BigDecimal remaining;
    /** 已用占额度百分比，保留两位；无额度时为 0 */
    private BigDecimal usagePercent;
    private List<BudgetCategoryDTO> categories;
}
