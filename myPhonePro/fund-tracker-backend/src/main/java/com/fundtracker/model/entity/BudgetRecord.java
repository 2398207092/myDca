package com.fundtracker.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 预算账本的一笔开销。
 * 只做台账，不扣「账户与现金」——与现金/持仓口径零耦合。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "budget_records")
public class BudgetRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** 数据归属用户 ID */
    @Column(length = 36)
    private String userId;

    @Column(nullable = false, length = 36)
    private String categoryId;

    /** 发生日期（年度必须与预算类型一致） */
    @Column(nullable = false)
    private LocalDate occurDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 200)
    private String note;

    @Builder.Default
    private boolean deleted = false;
}
