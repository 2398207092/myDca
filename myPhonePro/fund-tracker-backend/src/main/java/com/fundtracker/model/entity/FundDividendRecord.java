package com.fundtracker.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 基金历史分红记录（来自天天基金 fhsp 页面抓取）
 * 按除权日唯一（同一基金同一除息日只存一条）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "fund_dividend_records",
       uniqueConstraints = @UniqueConstraint(columnNames = {"fundCode", "exDate"}))
public class FundDividendRecord {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 10)
    private String fundCode;

    @Column(nullable = false)
    private LocalDate exDate;

    private LocalDate regDate;

    private LocalDate payDate;

    @Column(precision = 10, scale = 4)
    private BigDecimal dividendPerShare;

    private Integer dividendYear;

    @Column(length = 20)
    @Builder.Default
    private String source = "scrape";

    @Column(nullable = false)
    private LocalDateTime createdAt;

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