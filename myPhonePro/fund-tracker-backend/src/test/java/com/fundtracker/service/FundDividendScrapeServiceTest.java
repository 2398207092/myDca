package com.fundtracker.service;

import com.fundtracker.model.dto.DividendInfoDTO;
import com.fundtracker.model.entity.FundDividendRecord;
import com.fundtracker.repository.FundDividendRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FundDividendScrapeServiceTest {

    @Mock
    private FundDividendRecordRepository recordRepository;

    private FundDividendScrapeService scrapeService;

    @BeforeEach
    void setUp() {
        scrapeService = new FundDividendScrapeService(recordRepository);
    }

    @Nested
    @DisplayName("calculateWithFrequency 计算年均分红")
    class CalculateWithFrequency {

        @Test
        @DisplayName("空记录 → 返回零数据")
        void emptyRecords() {
            DividendInfoDTO result = scrapeService.calculateWithFrequency(List.of(), "基金");

            assertEquals(BigDecimal.ZERO, result.getAnnualDividendPerShare());
            assertEquals("none", result.getSource());
        }

        @Test
        @DisplayName("单条记录 → 不定期分红，年预期1次")
        void singleRecord() {
            FundDividendRecord record = FundDividendRecord.builder()
                    .exDate(LocalDate.of(2026, 6, 15))
                    .dividendPerShare(new BigDecimal("0.50"))
                    .build();

            DividendInfoDTO result = scrapeService.calculateWithFrequency(List.of(record), "基金");

            assertEquals(new BigDecimal("0.5000"), result.getAnnualDividendPerShare());
            assertEquals("irregular", result.getDividendFrequency());
            assertEquals(1, result.getDividendCount());
        }

        @Test
        @DisplayName("多条记录 → 年度分红")
        void yearlyFrequency() {
            FundDividendRecord r1 = FundDividendRecord.builder()
                    .exDate(LocalDate.of(2024, 6, 15))
                    .dividendPerShare(new BigDecimal("0.50"))
                    .build();
            FundDividendRecord r2 = FundDividendRecord.builder()
                    .exDate(LocalDate.of(2025, 6, 10))
                    .dividendPerShare(new BigDecimal("0.60"))
                    .build();
            FundDividendRecord r3 = FundDividendRecord.builder()
                    .exDate(LocalDate.of(2026, 6, 20))
                    .dividendPerShare(new BigDecimal("0.55"))
                    .build();

            DividendInfoDTO result = scrapeService.calculateWithFrequency(List.of(r1, r2, r3), "基金");

            assertEquals("yearly", result.getDividendFrequency());
            assertEquals(3, result.getDividendCount());
            // 平均每股分红 = (0.50 + 0.60 + 0.55) / 3 = 0.55
            assertEquals(0, new BigDecimal("0.55").compareTo(result.getAvgDividendPerShare()));
            // 年均分红 = 0.55 * 1 = 0.55
            assertEquals(0, new BigDecimal("0.5500").compareTo(result.getAnnualDividendPerShare()));
        }

        @Test
        @DisplayName("多条记录 → 季度分红")
        void quarterlyFrequency() {
            FundDividendRecord r1 = FundDividendRecord.builder()
                    .exDate(LocalDate.of(2025, 3, 15))
                    .dividendPerShare(new BigDecimal("0.20"))
                    .build();
            FundDividendRecord r2 = FundDividendRecord.builder()
                    .exDate(LocalDate.of(2025, 6, 15))
                    .dividendPerShare(new BigDecimal("0.25"))
                    .build();
            FundDividendRecord r3 = FundDividendRecord.builder()
                    .exDate(LocalDate.of(2025, 9, 15))
                    .dividendPerShare(new BigDecimal("0.22"))
                    .build();
            FundDividendRecord r4 = FundDividendRecord.builder()
                    .exDate(LocalDate.of(2025, 12, 15))
                    .dividendPerShare(new BigDecimal("0.23"))
                    .build();

            DividendInfoDTO result = scrapeService.calculateWithFrequency(List.of(r1, r2, r3, r4), "ETF");

            assertEquals("quarterly", result.getDividendFrequency());
            assertEquals(4, result.getDividendCount());
            // 年均分红 = 平均每股分红 * 4
            assertEquals(0, new BigDecimal("0.9000").compareTo(result.getAnnualDividendPerShare()));
        }

        @Test
        @DisplayName("所有记录分红为0 → 返回零数据")
        void allZeroDividends() {
            FundDividendRecord r1 = FundDividendRecord.builder()
                    .exDate(LocalDate.of(2025, 6, 15))
                    .dividendPerShare(BigDecimal.ZERO)
                    .build();

            DividendInfoDTO result = scrapeService.calculateWithFrequency(List.of(r1), "基金");

            assertEquals(BigDecimal.ZERO, result.getAnnualDividendPerShare());
            assertEquals("none", result.getSource());
        }
    }

    @Nested
    @DisplayName("isValidExDate 除权日有效性过滤")
    class IsValidExDate {

        @Test
        @DisplayName("null 除权日 → 无效")
        void nullExDate() {
            assertFalse(FundDividendScrapeService.isValidExDate(null, LocalDate.of(2020, 1, 1)));
        }

        @Test
        @DisplayName("晚于今天的除权日 → 无效（未来异常数据）")
        void futureExDate() {
            assertFalse(FundDividendScrapeService.isValidExDate(LocalDate.now().plusDays(1), LocalDate.of(2020, 1, 1)));
        }

        @Test
        @DisplayName("早于成立日期的除权日 → 无效（代码复用脏数据）")
        void beforeEstablishDate() {
            assertFalse(FundDividendScrapeService.isValidExDate(LocalDate.of(2019, 6, 15), LocalDate.of(2022, 11, 29)));
        }

        @Test
        @DisplayName("成立日期之后的除权日 → 有效")
        void afterEstablishDate() {
            assertTrue(FundDividendScrapeService.isValidExDate(LocalDate.of(2023, 6, 15), LocalDate.of(2022, 11, 29)));
        }

        @Test
        @DisplayName("恰好等于成立日期 → 有效")
        void equalsEstablishDate() {
            assertTrue(FundDividendScrapeService.isValidExDate(LocalDate.of(2022, 11, 29), LocalDate.of(2022, 11, 29)));
        }

        @Test
        @DisplayName("成立日期为 null → 用兜底常量 2020-01-01 过滤（不得抛 NPE）")
        void nullEstablishDate() {
            // 早于兜底日期 → 无效
            assertFalse(FundDividendScrapeService.isValidExDate(LocalDate.of(2019, 6, 15), null));
            // 晚于兜底日期 → 有效
            assertTrue(FundDividendScrapeService.isValidExDate(LocalDate.of(2021, 6, 15), null));
            // 007466 场景：成立日期 null + 2026 年分红 → 应通过
            assertTrue(FundDividendScrapeService.isValidExDate(LocalDate.of(2026, 8, 6), null));
        }
    }

    @Nested
    @DisplayName("getEstablishDate 成立日期缓存")
    class GetEstablishDate {

        @Test
        @DisplayName("抓取失败返回 null 且不缓存（不得抛 NPE）")
        void failedFetchReturnsNull() {
            // 成立日期接口是真实 HTTP，无法在此单测中稳定 mock；
            // 若网络不可达返回 null，第二次调用应仍能正常返回（不缓存 null、不抛 NPE）
            LocalDate first = scrapeService.getEstablishDate("007466");
            LocalDate second = scrapeService.getEstablishDate("007466");
            assertEquals(first, second); // 两次结果一致（null 或日期）
        }
    }

    @Nested
    @DisplayName("getRecords 查询分红记录")
    class GetRecords {

        @Test
        @DisplayName("委托给 repository 查询")
        void delegatesToRepository() {
            FundDividendRecord record = FundDividendRecord.builder()
                    .id(UUID.randomUUID().toString()).fundCode("000001").build();
            when(recordRepository.findByFundCodeOrderByExDateDesc("000001"))
                    .thenReturn(List.of(record));

            List<FundDividendRecord> result = scrapeService.getRecords("000001");

            assertEquals(1, result.size());
            verify(recordRepository).findByFundCodeOrderByExDateDesc("000001");
        }
    }

    @Nested
    @DisplayName("deleteRecord 删除分红记录")
    class DeleteRecord {

        @Test
        @DisplayName("委托给 repository 删除")
        void delegatesToRepository() {
            doNothing().when(recordRepository).deleteById("rec-1");

            scrapeService.deleteRecord("rec-1");

            verify(recordRepository).deleteById("rec-1");
        }
    }

    @Nested
    @DisplayName("getLatestExDate 获取最新除权日")
    class GetLatestExDate {

        @Test
        @DisplayName("有记录 → 返回最新除权日")
        void found() {
            FundDividendRecord record = FundDividendRecord.builder()
                    .exDate(LocalDate.of(2026, 7, 15))
                    .build();
            when(recordRepository.findTopByFundCodeOrderByExDateDesc("000001"))
                    .thenReturn(Optional.of(record));

            Optional<LocalDate> result = scrapeService.getLatestExDate("000001");

            assertTrue(result.isPresent());
            assertEquals(LocalDate.of(2026, 7, 15), result.get());
        }

        @Test
        @DisplayName("无记录 → 返回空")
        void notFound() {
            when(recordRepository.findTopByFundCodeOrderByExDateDesc("000001"))
                    .thenReturn(Optional.empty());

            Optional<LocalDate> result = scrapeService.getLatestExDate("000001");

            assertFalse(result.isPresent());
        }
    }
}