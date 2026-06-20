package com.fundtracker.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "asset_snapshots")
public class AssetSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal totalValue;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal cashValue;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal cryptoValue;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal usStockValue;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal goldValue;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal dividendValue;

    @Column(columnDefinition = "TEXT")
    private String breakdownJson; // 各类资产明细 JSON
}
