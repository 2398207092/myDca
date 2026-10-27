package com.fundtracker.model.enums;

public enum HoldingType {
    fund,        // 基金
    cny_asset,   // 人民币资产
    ETF,         // ETF
    A股,          // A股
    港股,         // 港股
    美股,         // 美股
    crypto,      // 加密币（2026-10-27 新增，配合 USDT 报价）
    自定义        // 自定义
}
