package com.fundtracker.model.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateHoldingReq {
    private String name;
    private String costAlgorithm;
    private BigDecimal shares;
    private BigDecimal cost;
    private BigDecimal marketValue;
    private String assetCategory;
}
