package com.fundtracker.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskTargetDTO {
    /** {mode:"A"|"B", targets:{dividend:60,...}}；未设置时为 null */
    private String riskTargetJson;
    private String updatedAt;
}