package com.fundtracker.model.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单持仓年化收益率响应。
 * 仅对 asset_category 为 us_stock / gold / dividend 的持仓计算。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnualizedReturnDTO {

    private String holdingId;
    /** 年化收益率百分比，无法计算时为 null */
    private BigDecimal annualizedReturn;
    /** 总投入金额（所有 buy 的 total 之和） */
    private BigDecimal totalInvested;
    /** 总回收金额（所有 sell 的 total 之和） */
    private BigDecimal totalWithdrawn;
    /** 当前市值 */
    private BigDecimal currentValue;
    /** 持有天数 */
    private Integer holdingDays;
    /** 首次交易日期 */
    private LocalDate firstTransactionDate;
    /** IRR 原始值（未年化），无法计算时为 null */
    private BigDecimal irr;
}
