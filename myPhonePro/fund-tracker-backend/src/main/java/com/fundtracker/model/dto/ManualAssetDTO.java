package com.fundtracker.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualAssetDTO {
    private String id;
    private String name;
    private String type; // crypto / cash
    private BigDecimal amount;
    private String currency;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
