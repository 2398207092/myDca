package com.fundtracker.model.entity;

import com.fundtracker.model.enums.ForecastHorizon;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_settings")
public class UserSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private String currencyLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ForecastHorizon forecastHorizon;

    @Column(precision = 10, scale = 4)
    private BigDecimal customForecastValue;

    @Column(nullable = false)
    private boolean notificationsEnabled;
}
