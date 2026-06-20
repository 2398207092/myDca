package com.fundtracker.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "exchange_rates")
public class ExchangeRate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String pair;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal rate;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
