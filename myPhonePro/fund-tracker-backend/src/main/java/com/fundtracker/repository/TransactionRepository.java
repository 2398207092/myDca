package com.fundtracker.repository;

import com.fundtracker.model.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, String> {
    List<Transaction> findByHoldingIdOrderByDateDesc(String holdingId);
    List<Transaction> findByHoldingId(String holdingId);
    List<Transaction> findByHoldingIdOrderByDateAsc(String holdingId);
    List<Transaction> findByUserIdOrderByDateDesc(String userId);
    List<Transaction> findByHoldingIdAndUserIdOrderByDateDesc(String holdingId, String userId);
    List<Transaction> findByHoldingIdAndUserIdOrderByDateAsc(String holdingId, String userId);
    List<Transaction> findByHoldingIdAndUserId(String holdingId, String userId);

    @Query("SELECT MIN(t.date) FROM Transaction t WHERE t.holdingId IN :holdingIds")
    LocalDate findEarliestTransactionDateByHoldingIds(List<String> holdingIds);

    @Query("SELECT MIN(t.date) FROM Transaction t WHERE t.holdingId = :holdingId")
    Optional<LocalDate> findEarliestTransactionDateByHoldingId(String holdingId);

    int deleteByHoldingId(String holdingId);
}
