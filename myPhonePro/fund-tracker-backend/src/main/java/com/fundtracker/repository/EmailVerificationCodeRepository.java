package com.fundtracker.repository;

import com.fundtracker.model.entity.EmailVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, String> {

    /** 查询某邮箱某类型的最新未使用验证码 */
    Optional<EmailVerificationCode> findTopByEmailAndTypeAndUsedFalseOrderByCreatedAtDesc(String email, String type);

    /** 统计某邮箱今天已发送的验证码数量 */
    long countByEmailAndCreatedAtAfter(String email, LocalDateTime after);
}
