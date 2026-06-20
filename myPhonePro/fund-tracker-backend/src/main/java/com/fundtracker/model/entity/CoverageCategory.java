package com.fundtracker.model.entity;

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
@Table(name = "coverage_categories")
public class CoverageCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String icon;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(nullable = false, length = 10)
    private String color;
}
