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

    public static BusinessException manualAssetNotFound() {
        return new BusinessException(6003, "手动资产不存在");
    }

    public static BusinessException manualAssetAccessDenied() {
        return new BusinessException(6004, "无权访问该资产");
    }

    /** 通用业务错误，用于登录/验证码等场景 */
    public static BusinessException bizError(String message) {
        return new BusinessException(7001, message);
    }

    /** 无权限访问（管理接口 / 越权操作） */
    public static BusinessException forbidden(String message) {
        return new BusinessException(403, message);
    }
}
