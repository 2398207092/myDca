package com.fundtracker.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateDcaPlanReq {
    private BigDecimal amount;
    private String frequency; // daily / weekly / biweekly / monthly
    private Integer day;
    private String status; // active / paused / ended
}
