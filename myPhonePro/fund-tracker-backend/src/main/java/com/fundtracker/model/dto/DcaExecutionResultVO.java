package com.fundtracker.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DcaExecutionResultVO {
    private String transactionId;
    private BigDecimal amount;
    private BigDecimal quantity;
    private BigDecimal navPrice;
    private LocalDate navDate;
    private LocalDate executionDate;
    private String holdingName;
    private String holdingCode;
}
