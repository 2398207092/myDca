package com.fundtracker.model.entity;

import com.fundtracker.model.enums.TransactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String holdingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal price;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal fee;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, length = 10)
    private String source;  // "manual" / "dca"

    @Column(length = 36)
    private String dcaPlanId;  // nullable, FK to dca_plans
}
