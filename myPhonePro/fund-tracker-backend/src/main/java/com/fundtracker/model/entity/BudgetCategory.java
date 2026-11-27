package com.fundtracker.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 年度预算类型（如「餐饮」「日用」）。
 * 与 live_expenses（固定月度开销 / 分红覆盖度）是两套独立口径，互不影响。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "budget_categories")
public class BudgetCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** 数据归属用户 ID */
    @Column(length = 36)
    private String userId;

    /** 预算年度（列名避开 MySQL 关键字 year） */
    @Column(name = "budget_year", nullable = false)
    private Integer year;

    @Column(nullable = false, length = 30)
    private String name;

    /** 年度额度 */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    private Integer sortOrder;

    @Builder.Default
    private boolean deleted = false;
}
