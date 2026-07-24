package com.fundtracker.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "email_verification_codes", indexes = {
    @Index(name = "idx_vcode_email", columnList = "email"),
    @Index(name = "idx_vcode_created", columnList = "createdAt")
})
public class EmailVerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 255)
    private String email;

    /** 6 位数字验证码 */
    @Column(nullable = false, length = 6)
    private String code;

    /** login / register / set_password */
    @Column(nullable = false, length = 20)
    private String type;

    /** 过期时间（发送后 5 分钟） */
    @Column(nullable = false)
    private LocalDateTime expireAt;

    /** 是否已使用 */
    @Column(nullable = false)
    @Builder.Default
    private boolean used = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
