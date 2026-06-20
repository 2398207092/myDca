package com.fundtracker.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 分红信息 - 年均每份分红
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendInfoDTO {
    /** 年均每份分红金额 */
    private BigDecimal annualDividendPerShare;
    /** 单位文字: 每份 / 每股 */
    private String unitText;
    /** 数据来源: api / scrape_temp / none */
    private String source;
    /** 分红频率: monthly/quarterly/yearly/irregular */
    private String dividendFrequency;
    /** 分红频率描述: 月度分红/季度分红/年度分红/不定期分红 */
    private String dividendFrequencyDesc;
    /** 统计的分红次数 */
    private Integer dividendCount;
    /** 单次分红平均值 */
    private BigDecimal avgDividendPerShare;
}
