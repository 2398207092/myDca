package com.fundtracker.model.entity;

import com.fundtracker.model.enums.EventStatus;
import com.fundtracker.model.enums.EventType;
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
@Table(name = "dividend_events")
public class DividendEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** 数据归属用户 ID */
    @Column(length = 36)
    private String userId;

    @Column(nullable = false)
    private String holdingId;

    @Column(nullable = false)
    private String holdingName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType type;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Boolean participated;

    /** 是否已转为复投（防止重复复投） */
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private Boolean converted = false;
}
