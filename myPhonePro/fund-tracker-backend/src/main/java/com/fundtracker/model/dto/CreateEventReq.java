package com.fundtracker.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateEventReq {
    @NotBlank
    private String holdingId;

    @NotBlank
    private String type;

    @NotBlank
    private String date;

    private BigDecimal amount;
    private String description;
}
