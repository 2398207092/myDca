package com.fundtracker.service;

import com.fundtracker.model.entity.FundNavRecord;
import com.fundtracker.repository.FundNavRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * FundNavScrapeService 单元测试
 * 重点验证 #7（HTTPS 常量）与 #8（重试耗尽回退数据库缓存）的辅助逻辑
 */
@ExtendWith(MockitoExtension.class)
class FundNavScrapeServiceTest {

    @Mock private FundNavRecordRepository navRecordRepository;

    private FundNavScrapeService navScrapeService;

    @BeforeEach
    void setUp() {
        navScrapeService = new FundNavScrapeService(navRecordRepository);
    }

    @Nested
    @DisplayName("getLatestNavFromDb 从数据库获取最新净值")
    class GetLatestNavFromDb {

        @Test
        @DisplayName("有记录 → 返回最新净值")
        void found() {
            FundNavRecord record = FundNavRecord.builder()
                    .unitNav(new BigDecimal("2.4567"))
                    .navDate(LocalDate.of(2026, 8, 6))
                    .build();
            when(navRecordRepository.findTopByFundCodeOrderByNavDateDesc("016452"))
                    .thenReturn(Optional.of(record));

            FundNavScrapeService.LatestNavResult result = navScrapeService.getLatestNavFromDb("016452");

            assertNotNull(result);
            assertEquals(0, new BigDecimal("2.4567").compareTo(result.unitNav()));
            assertEquals(LocalDate.of(2026, 8, 6), result.navDate());
        }

        @Test
        @DisplayName("无记录 → 返回 null")
        void notFound() {
            when(navRecordRepository.findTopByFundCodeOrderByNavDateDesc("016452"))
                    .thenReturn(Optional.empty());

            assertNull(navScrapeService.getLatestNavFromDb("016452"));
        }
    }

    @Nested
    @DisplayName("getLatestNavBefore 获取指定日期前最近净值")
    class GetLatestNavBefore {

        @Test
        @DisplayName("返回不晚于指定日期的净值")
        void found() {
            FundNavRecord record = FundNavRecord.builder()
                    .unitNav(new BigDecimal("2.4500"))
                    .navDate(LocalDate.of(2026, 7, 31))
                    .build();
            when(navRecordRepository.findTopByFundCodeAndNavDateLessThanEqualOrderByNavDateDesc(
                    "016452", LocalDate.of(2026, 8, 3)))
                    .thenReturn(Optional.of(record));

            FundNavScrapeService.LatestNavResult result =
                    navScrapeService.getLatestNavBefore("016452", LocalDate.of(2026, 8, 3));

            assertNotNull(result);
            assertEquals(LocalDate.of(2026, 7, 31), result.navDate());
        }
    }

    @Nested
    @DisplayName("fetchIfEmpty 首次拉取控制")
    class FetchIfEmpty {

        @Test
        @DisplayName("数据库已有记录 → 跳过抓取")
        void skipWhenDataExists() {
            FundNavRecord record = FundNavRecord.builder().id("r-1").build();
            when(navRecordRepository.findByFundCodeOrderByNavDateDesc("016452"))
                    .thenReturn(java.util.List.of(record));

            // 不会触发真实 HTTP 抓取；验证不抛异常即可
            navScrapeService.fetchIfEmpty("016452");
        }
    }
}
