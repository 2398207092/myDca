package com.fundtracker.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BusinessException holdingNotFound() {
        return new BusinessException(1001, "持仓不存在");
    }

    public static BusinessException holdingCodeExists() {
        return new BusinessException(1002, "持仓代码已存在");
    }

    public static BusinessException eventNotFound() {
        return new BusinessException(2001, "分红事件不存在");
    }

    public static BusinessException transactionNotFound() {
        return new BusinessException(3001, "交易记录不存在");
    }

    public static BusinessException insufficientShares() {
        return new BusinessException(3002, "卖出份额不足");
    }

    public static BusinessException invalidTransactionType() {
        return new BusinessException(3003, "无效的交易类型");
    }

    public static BusinessException rateLimitExceeded() {
        return new BusinessException(4001, "刷新过于频繁，请稍后再试");
    }

    public static BusinessException expenseNotFound(String id) {
        return new BusinessException(5001, "支出记录不存在: " + id);
    }

    public static BusinessException planNotFound() {
        return new BusinessException(6001, "定投计划不存在");
    }

    public static BusinessException invalidParam(String message) {
        return new BusinessException(6002, message);
    }
}
