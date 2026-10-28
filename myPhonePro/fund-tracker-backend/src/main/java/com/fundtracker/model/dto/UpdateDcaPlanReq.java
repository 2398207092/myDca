package com.fundtracker.model.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 编辑定投计划请求。
 *
 * <p>所有字段均可为空 —— 只更新传入的字段（部分更新）。
 * 注意：不支持修改标的（holdingId）。累计投入/份额属于原标的，换标的会导致统计串账，
 * 正确做法是删除旧计划后新建。
 */
@Data
public class UpdateDcaPlanReq {

    /** 每期金额，不可小于 1 元；为空表示不修改 */
    @DecimalMin(value = "1", message = "每期金额不能小于 1 元")
    private BigDecimal amount;

    /** daily / weekly / biweekly / monthly；为空表示不修改 */
    private String frequency;

    /** 扣款日：weekly/biweekly 为 1~7（周一~周日），monthly 为 1~31，daily 忽略 */
    private Integer day;

    /** active / paused / ended；为空表示不修改 */
    private String status;

    /** 扣款账户（可空）：执行时从该账户扣现金/USDT；传空串清除关联 */
    private String accountId;
}
