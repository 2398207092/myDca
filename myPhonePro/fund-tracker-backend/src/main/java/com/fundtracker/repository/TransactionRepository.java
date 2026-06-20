package com.fundtracker.repository;

import com.fundtracker.model.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, String> {
    List<Transaction> findByHoldingIdOrderByDateDesc(String holdingId);
    List<Transaction> findByHoldingId(String holdingId);

    @Query("SELECT MIN(t.date) FROM Transaction t WHERE t.holdingId IN :holdingIds")
    LocalDate findEarliestTransactionDateByHoldingIds(List<String> holdingIds);

    int deleteByHoldingId(String holdingId);
}
