package com.fundtracker.model.entity;

import com.fundtracker.model.enums.MembershipType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_profiles")
public class UserProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1024)
    private String avatar;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipType membership;

    private LocalDate membershipExpiry;

    @Column(nullable = false)
    private String phone;
}
