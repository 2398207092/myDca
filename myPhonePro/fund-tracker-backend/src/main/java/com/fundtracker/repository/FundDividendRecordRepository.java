package com.fundtracker.repository;

import com.fundtracker.model.entity.FundDividendRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FundDividendRecordRepository extends JpaRepository<FundDividendRecord, String> {

    List<FundDividendRecord> findByFundCodeOrderByExDateDesc(String fundCode);

    List<FundDividendRecord> findByFundCodeAndExDateAfterOrderByExDateAsc(String fundCode, LocalDate after);

    Optional<FundDividendRecord> findTopByFundCodeOrderByExDateDesc(String fundCode);

    List<FundDividendRecord> findByFundCodeIn(List<String> fundCodes);

    boolean existsByFundCodeAndExDate(String fundCode, LocalDate exDate);

    /**
     * 找出近3年分红中超过均值3倍的异常记录
     */
    @Query(value = """
            SELECT r.fund_code, r.ex_date, r.dividend_per_share
            FROM fund_dividend_records r
            JOIN (
              SELECT fund_code, AVG(dividend_per_share) * 3 AS threshold
              FROM fund_dividend_records
              WHERE ex_date >= DATE_SUB(CURDATE(), INTERVAL 3 YEAR)
              GROUP BY fund_code
            ) a ON r.fund_code = a.fund_code
            WHERE r.dividend_per_share > a.threshold
              AND r.ex_date >= DATE_SUB(CURDATE(), INTERVAL 3 YEAR)
            ORDER BY r.fund_code, r.ex_date
            """, nativeQuery = true)
    List<Object[]> findDividendOutliers();
}