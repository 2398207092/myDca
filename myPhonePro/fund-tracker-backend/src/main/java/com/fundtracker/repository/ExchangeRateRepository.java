package com.fundtracker.repository;

import com.fundtracker.model.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, String> {
    Optional<ExchangeRate> findByPair(String pair);
}
