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
@Table(name = "live_expenses")
public class LiveExpense {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    private String icon;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyAmount;

    private Integer sortOrder;

    @Builder.Default
    private boolean deleted = false;
}
