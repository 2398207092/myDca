package com.fundtracker.repository;

import com.fundtracker.model.entity.FundDividendRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FundDividendRecordRepository extends JpaRepository<FundDividendRecord, String> {

    List<FundDividendRecord> findByFundCodeOrderByExDateDesc(String fundCode);

    List<FundDividendRecord> findByFundCodeAndExDateAfterOrderByExDateAsc(String fundCode, LocalDate after);

    Optional<FundDividendRecord> findTopByFundCodeOrderByExDateDesc(String fundCode);

    List<FundDividendRecord> findByFundCodeIn(List<String> fundCodes);

    boolean existsByFundCodeAndExDate(String fundCode, LocalDate exDate);
}