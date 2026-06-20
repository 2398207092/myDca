package com.fundtracker.model.entity;

import com.fundtracker.model.enums.DcaFrequency;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "dca_plans")
public class DcaPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String holdingId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DcaFrequency frequency;

    @Column
    private Integer day; // 非daily时使用：月(1-31)或周(1-7)

    @Column(nullable = false, length = 20)
    private String tradingMarket; // china / us / crypto

    @Column(nullable = false, length = 10)
    private String status; // active / paused / ended

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal totalInvested;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal totalShares;

    @Column(nullable = false)
    private Integer totalExecutions;

    @Column(nullable = false)
    private LocalDate nextExecutionDate;

    @Column
    private LocalDateTime lastExecutedAt;

    @Column(nullable = false)
    private LocalDate startedAt;

    @Column
    private LocalDate endedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

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
