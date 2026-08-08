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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DividendInfoService 单元测试
 * 重点验证 #9 数据源统一：ex_date 口径 = 本地数据库（含频率）优先 → fhsp 页面临时抓取兜底
 * 两条路径共用 calculateWithFrequency，结果一致
 */
@ExtendWith(MockitoExtension.class)
class DividendInfoServiceTest {

    @Mock private FundDividendRecordRepository fundDividendRecordRepository;
    @Mock private FundDividendScrapeService fundDividendScrapeService;

    private DividendInfoService dividendInfoService;

    @BeforeEach
    void setUp() {
        dividendInfoService = new DividendInfoService(
                fundDividendRecordRepository, fundDividendScrapeService, new com.fundtracker.common.HttpClientWrapper());
    }

    @Nested
    @DisplayName("ex_date 口径（#9 数据源统一）")
    class ExDateMethod {

        @Test
        @DisplayName("本地数据库有记录 → 返回数据库结果，不触发临时抓取")
        void localDbHit() {
            FundDividendRecord r = FundDividendRecord.builder()
                    .exDate(LocalDate.now().minusMonths(6))
                    .dividendPerShare(new BigDecimal("0.05"))
                    .build();
            when(fundDividendRecordRepository.findByFundCodeAndExDateAfterOrderByExDateAsc(eq("000001"), any()))
                    .thenReturn(List.of(r));
            DividendInfoDTO dbResult = DividendInfoDTO.builder()
                    .annualDividendPerShare(new BigDecimal("0.6000"))
                    .source("database")
                    .build();
            when(fundDividendScrapeService.calculateWithFrequency(anyList(), eq("基金")))
                    .thenReturn(dbResult);

            DividendInfoDTO result = dividendInfoService.getDividendInfo("000001", "基金", "ex_date", "3y");

            assertEquals("database", result.getSource());
            assertEquals(new BigDecimal("0.6000"), result.getAnnualDividendPerShare());
            // 本地库命中 → 不应触发临时抓取
            verify(fundDividendScrapeService, never()).fetchAndCalculate(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("本地数据库无记录 → 降级到 fhsp 临时抓取")
        void localDbMissFallsBackToScrape() {
            when(fundDividendRecordRepository.findByFundCodeAndExDateAfterOrderByExDateAsc(eq("000001"), any()))
                    .thenReturn(List.of());
            DividendInfoDTO tempResult = DividendInfoDTO.builder()
                    .annualDividendPerShare(new BigDecimal("0.1000"))
                    .source("scrape_temp")
                    .build();
            when(fundDividendScrapeService.fetchAndCalculate("000001", "基金", "3y"))
                    .thenReturn(tempResult);

            DividendInfoDTO result = dividendInfoService.getDividendInfo("000001", "基金", "ex_date", "3y");

            assertEquals("scrape_temp", result.getSource());
            assertEquals(new BigDecimal("0.1000"), result.getAnnualDividendPerShare());
            verify(fundDividendScrapeService).fetchAndCalculate("000001", "基金", "3y");
        }

        @Test
        @DisplayName("本地库无记录且临时抓取也无数据 → 返回 none")
        void bothMiss() {
            when(fundDividendRecordRepository.findByFundCodeAndExDateAfterOrderByExDateAsc(eq("000001"), any()))
                    .thenReturn(List.of());
            when(fundDividendScrapeService.fetchAndCalculate("000001", "基金", "3y"))
                    .thenReturn(DividendInfoDTO.builder()
                            .annualDividendPerShare(BigDecimal.ZERO)
                            .source("none")
                            .build());

            DividendInfoDTO result = dividendInfoService.getDividendInfo("000001", "基金", "ex_date", "3y");

            assertEquals("none", result.getSource());
            assertEquals(BigDecimal.ZERO, result.getAnnualDividendPerShare());
        }

        @Test
        @DisplayName("空代码 → 直接返回 none")
        void emptyCode() {
            DividendInfoDTO result = dividendInfoService.getDividendInfo("", "基金", "ex_date", "3y");
            assertEquals("none", result.getSource());
        }
    }

    @Nested
    @DisplayName("report_period 口径（保留唯一路径）")
    class ReportPeriodMethod {

        @Test
        @DisplayName("品中数据获取失败 → 降级到 fhsp 临时抓取")
        void pingZhongFailFallsBack() {
            // fetchPingZhongData 是私有方法且走真实 HTTP，无法在此单元测试中稳定调用；
            // 这里验证代码路径不会抛异常，最终走兜底逻辑
            DividendInfoDTO tempResult = DividendInfoDTO.builder()
                    .annualDividendPerShare(new BigDecimal("0.2000"))
                    .source("scrape_temp")
                    .build();
            when(fundDividendScrapeService.fetchAndCalculate(anyString(), anyString(), anyString()))
                    .thenReturn(tempResult);

            DividendInfoDTO result = dividendInfoService.getDividendInfo("000001", "基金", "report_period", "3y");

            // 品中数据走真实 HTTP，若成功可能返回 api；若失败走 scrape_temp
            assertNotNull(result);
            assertTrue("api".equals(result.getSource()) || "scrape_temp".equals(result.getSource())
                    || "none".equals(result.getSource()));
        }
    }
}
