package com.fundtracker.model.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateManualAssetReq {
    private String name;
    private String type; // crypto / cash
    private BigDecimal amount;
    private String currency;
    private String accountId;
    private String assetCode;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private String priceSource;
    private String note;
}
