package com.fundtracker.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendEventDTO {
    private String id;
    private String holdingId;
    private String holdingName;
    private String type;
    private String date;
    private BigDecimal amount;
    private String status;
    private String description;
}
