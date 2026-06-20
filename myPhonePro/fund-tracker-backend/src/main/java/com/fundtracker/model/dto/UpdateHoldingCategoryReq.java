package com.fundtracker.model.dto;

import lombok.Data;

@Data
public class UpdateHoldingCategoryReq {
    private String assetCategory; // us_stock / gold / dividend / null or "" (clear)
}
