package com.fundtracker.model.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateUserSettingsReq {
    private String currency;
    private String currencyLabel;
    private String forecastHorizon;
    private BigDecimal customForecastValue;
    private Boolean notificationsEnabled;
}
