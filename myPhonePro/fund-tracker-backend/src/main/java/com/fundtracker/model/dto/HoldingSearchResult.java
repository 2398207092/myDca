package com.fundtracker.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldingSearchResult {
    /** 基金/股票代码 */
    private String code;
    /** 基金/股票名称 */
    private String name;
    /** 类型：ETF / 基金 / A股 / 港股 / 美股 */
    private String type;
    /** 拼音缩写 */
    private String pinyin;
    /** 最新净值 */
    private String netWorth;
    /** 基金名称（全称） */
    private String fullName;
}
