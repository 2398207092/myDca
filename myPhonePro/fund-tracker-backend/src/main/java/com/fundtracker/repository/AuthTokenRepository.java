package com.fundtracker.repository;

import com.fundtracker.model.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, String> {
    Optional<AuthToken> findByTokenAndActiveTrue(String token);
}
