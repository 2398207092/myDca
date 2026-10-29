package com.fundtracker.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "manual_assets")
public class ManualAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** 数据归属用户 ID */
    @Column(length = 36)
    private String userId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private String type; // crypto / cash

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    /** 归属账户 ID；历史数据为空时按默认账户处理 */
    @Column(length = 36)
    private String accountId;

    /** 资产代码：CNY / USDT / BTC / ETH（现金为 CNY 或账户本位币） */
    @Column(length = 20)
    private String assetCode;

    /** 数量：加密币为持仓数量，现金为 NULL */
    @Column(precision = 24, scale = 8)
    private BigDecimal quantity;

    /** 单价（USD）：加密币为币价，现金为 NULL */
    @Column(precision = 24, scale = 8)
    private BigDecimal unitPrice;

    /** 价格来源：okx / gate / manual */
    @Column(length = 20)
    private String priceSource;

    @Column
    private LocalDateTime priceUpdatedAt;

    @Column(length = 10)
    private String currency; // CNY / USD / default: CNY

    @Column(length = 200)
    private String note;

    @Column(nullable = false)
    private boolean isPrimary; // 是否为主现金账户，自动记账默认操作此账户

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** 乐观锁版本号：并发读改写时失败方报错回滚，而非静默丢更新（P1-29/并发簇修复） */
    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
