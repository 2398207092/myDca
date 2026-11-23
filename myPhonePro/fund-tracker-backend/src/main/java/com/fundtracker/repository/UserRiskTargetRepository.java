package com.fundtracker.repository;

import com.fundtracker.model.entity.UserRiskTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRiskTargetRepository extends JpaRepository<UserRiskTarget, String> {
    Optional<UserRiskTarget> findByUserId(String userId);
}