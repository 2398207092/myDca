package com.fundtracker.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DcaPlanVO {
    private String id;
    private String holdingId;
    private String holdingName;
    private String holdingCode;
    private BigDecimal amount;
    private String frequency;
    private Integer day;
    private String tradingMarket;
    private String status;
    private BigDecimal totalInvested;
    private BigDecimal totalShares;
    private Integer totalExecutions;
    private LocalDate nextExecutionDate;
    private LocalDateTime lastExecutedAt;
    private LocalDate startedAt;
    private LocalDate endedAt;
    private LocalDateTime createdAt;
}
