package com.fundtracker.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 资产账户。一个账户是「一组资产」的容器，可以同时持有现金与多种加密币。
 * 例如：日常（CNY）、理财（CNY）、欧易（USDT，内含 USDT/BTC/ETH）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "accounts", indexes = {
    @Index(name = "idx_accounts_user", columnList = "userId"),
    @Index(name = "idx_accounts_deleted", columnList = "deleted")
})
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** 数据归属用户 ID */
    @Column(length = 36)
    private String userId;

    @Column(nullable = false, length = 50)
    private String name;

    /** daily 日常 / invest 理财 / exchange 交易所 / other 其他 */
    @Column(nullable = false, length = 20)
    private String purpose;

    /** 账户本位币：CNY / USDT。仅用于显示与扣款，账户内可同时持有多种资产 */
    @Column(name = "base_currency", nullable = false, length = 10)
    @Builder.Default
    private String baseCurrency = "CNY";

    /** 默认账户：交易/定投未指定账户时使用 */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
