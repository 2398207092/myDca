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
public class UserSettingsDTO {
    private String currency;
    private String currencyLabel;
    private String forecastHorizon;
    private BigDecimal customForecastValue;
    private boolean notificationsEnabled;
}
