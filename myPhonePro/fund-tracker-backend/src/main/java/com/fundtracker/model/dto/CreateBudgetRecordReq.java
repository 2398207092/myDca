package com.fundtracker.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateBudgetRecordReq {
    private String categoryId;
    private LocalDate occurDate;
    private BigDecimal amount;
    private String note;
}
