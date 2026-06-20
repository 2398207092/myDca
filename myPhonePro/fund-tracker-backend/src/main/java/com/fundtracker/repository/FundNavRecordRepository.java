package com.fundtracker.repository;

import com.fundtracker.model.entity.FundNavRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FundNavRecordRepository extends JpaRepository<FundNavRecord, String> {

    List<FundNavRecord> findByFundCodeOrderByNavDateDesc(String fundCode);

    Optional<FundNavRecord> findTopByFundCodeOrderByNavDateDesc(String fundCode);

    List<FundNavRecord> findByFundCodeAndNavDateAfterOrderByNavDateAsc(String fundCode, LocalDate after);

    List<FundNavRecord> findByFundCodeIn(List<String> fundCodes);

    boolean existsByFundCodeAndNavDate(String fundCode, LocalDate navDate);

    Optional<FundNavRecord> findTopByFundCodeOrderByNavDateAsc(String fundCode);

    // 获取指定日期之前（含当天）最近的一条净值
    Optional<FundNavRecord> findTopByFundCodeAndNavDateLessThanEqualOrderByNavDateDesc(String fundCode, LocalDate navDate);
}
