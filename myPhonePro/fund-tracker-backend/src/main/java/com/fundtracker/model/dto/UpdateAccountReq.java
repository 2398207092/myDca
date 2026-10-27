package com.fundtracker.model.dto;

import lombok.Data;

@Data
public class UpdateAccountReq {
    private String name;
    private String purpose;
    private String baseCurrency;
    private Boolean isDefault;
    private Integer sortOrder;
}
