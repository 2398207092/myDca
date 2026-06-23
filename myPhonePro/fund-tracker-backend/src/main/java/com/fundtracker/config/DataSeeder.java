package com.fundtracker.config;

import com.fundtracker.model.entity.*;
import com.fundtracker.model.enums.*;
import com.fundtracker.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Order(2)
public class DataSeeder implements CommandLineRunner {

    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;
    private final DividendEventRepository eventRepository;
    private final CoverageCategoryRepository coverageCategoryRepository;
    private final ExchangeRateRepository exchangeRateRepository;

    @Override
    public void run(String... args) {
        if (holdingRepository.count() > 0) {
            System.out.println(">>> 数据库已有数据，跳过初始化");
            return;
        }

        System.out.println(">>> 开始初始化种子数据...");

        // 1. 持仓
        Holding h1 = createHolding("招商中证白酒指数(LOF)A", "161725", HoldingType.fund,
                CostAlgorithm.diluted, new BigDecimal("5000.0000"),
                new BigDecimal("189.50"), "#FF7A45");
        h1.setMarketValue(new BigDecimal("8500.00"));
        h1.setCost(new BigDecimal("6500.00"));
        h1.setTotalDividendReceived(new BigDecimal("350.00"));
        h1.setNetInvestment(new BigDecimal("6150.00"));
        h1.setDividendRate(new BigDecimal("3.51"));
        h1.setPriceDividendRate(new BigDecimal("2.78"));
        h1.setDividendRecoveryRate(new BigDecimal("5.69"));
        h1.setEstimatedRecoveryYears(new BigDecimal("16.98"));
        holdingRepository.save(h1);

        Holding h2 = createHolding("长江电力", "600900", HoldingType.fund,
                CostAlgorithm.diluted, new BigDecimal("2000.0000"),
                new BigDecimal("150.00"), "#4CAF50");
        h2.setMarketValue(new BigDecimal("5400.00"));
        h2.setCost(new BigDecimal("3200.00"));
        h2.setTotalDividendReceived(new BigDecimal("120.00"));
        h2.setNetInvestment(new BigDecimal("3080.00"));
        h2.setDividendRate(new BigDecimal("4.69"));
        h2.setPriceDividendRate(new BigDecimal("2.78"));
        h2.setDividendRecoveryRate(new BigDecimal("3.90"));
        h2.setEstimatedRecoveryYears(new BigDecimal("19.73"));
        holdingRepository.save(h2);

        Holding h3 = createHolding("沪深300指数ETF", "510300", HoldingType.fund,
                CostAlgorithm.diluted, new BigDecimal("3000.0000"),
                new BigDecimal("120.00"), "#2196F3");
        h3.setMarketValue(new BigDecimal("5000.00"));
        h3.setCost(new BigDecimal("4500.00"));
        h3.setTotalDividendReceived(new BigDecimal("80.00"));
        h3.setNetInvestment(new BigDecimal("4420.00"));
        h3.setDividendRate(new BigDecimal("2.67"));
        h3.setPriceDividendRate(new BigDecimal("2.40"));
        h3.setDividendRecoveryRate(new BigDecimal("1.81"));
        h3.setEstimatedRecoveryYears(new BigDecimal("36.17"));
        holdingRepository.save(h3);

        System.out.println(">>> 已创建 3 条持仓");

        // 2. 交易记录
        transactionRepository.save(createTransaction(h1.getId(), TransactionType.buy, "2024-01-15",
                new BigDecimal("2000.0000"), new BigDecimal("1.2000"), new BigDecimal("5.00")));
        transactionRepository.save(createTransaction(h1.getId(), TransactionType.buy, "2024-03-10",
                new BigDecimal("1500.0000"), new BigDecimal("1.3500"), new BigDecimal("3.50")));
        transactionRepository.save(createTransaction(h1.getId(), TransactionType.buy, "2024-06-20",
                new BigDecimal("1500.0000"), new BigDecimal("1.3000"), new BigDecimal("4.00")));
        transactionRepository.save(createTransaction(h2.getId(), TransactionType.buy, "2024-02-01",
                new BigDecimal("2000.0000"), new BigDecimal("1.8000"), new BigDecimal("5.00")));
        transactionRepository.save(createTransaction(h3.getId(), TransactionType.buy, "2024-04-15",
                new BigDecimal("3000.0000"), new BigDecimal("1.5000"), new BigDecimal("4.50")));
        transactionRepository.save(createTransaction(h1.getId(), TransactionType.reinvest, "2024-06-01",
                new BigDecimal("100.0000"), new BigDecimal("1.2800"), new BigDecimal("0.00")));
        System.out.println(">>> 已创建 6 条交易记录");

        // 3. 分红事件
        eventRepository.save(createEvent(h1.getId(), h1.getName(), EventType.payout, "2024-11-15",
                new BigDecimal("42.50"), EventStatus.pending));
        eventRepository.save(createEvent(h1.getId(), h1.getName(), EventType.announcement, "2024-11-08",
                BigDecimal.ZERO, EventStatus.pending));
        eventRepository.save(createEvent(h2.getId(), h2.getName(), EventType.payout, "2024-11-20",
                new BigDecimal("28.00"), EventStatus.pending));
        eventRepository.save(createEvent(h2.getId(), h2.getName(), EventType.registration, "2024-11-12",
                BigDecimal.ZERO, EventStatus.pending));
        eventRepository.save(createEvent(h3.getId(), h3.getName(), EventType.ex_dividend, "2024-11-18",
                BigDecimal.ZERO, EventStatus.pending));
        eventRepository.save(createEvent(h1.getId(), h1.getName(), EventType.payout, "2024-08-10",
                new BigDecimal("35.00"), EventStatus.distributed));
        eventRepository.save(createEvent(h2.getId(), h2.getName(), EventType.payout, "2024-08-15",
                new BigDecimal("22.00"), EventStatus.distributed));
        System.out.println(">>> 已创建 7 条分红事件");

        // 4. 覆盖类目
        coverageCategoryRepository.save(createCategory("话费", "phone_android", 80, "#FF7A45"));
        coverageCategoryRepository.save(createCategory("养车", "directions_car", 40, "#4CAF50"));
        coverageCategoryRepository.save(createCategory("娱乐", "confirmation_number", 30, "#9C27B0"));
        coverageCategoryRepository.save(createCategory("医药", "medical_services", 25, "#2196F3"));
        coverageCategoryRepository.save(createCategory("午餐", "restaurant", 60, "#FF9800"));
        System.out.println(">>> 已创建 5 条覆盖类目");

        // 5. 汇率
        exchangeRateRepository.save(createRate("HKD/CNY", "港币/人民币", new BigDecimal("0.9245")));
        exchangeRateRepository.save(createRate("USD/CNY", "美金/人民币", new BigDecimal("7.2341")));
        System.out.println(">>> 已创建 2 条汇率数据");

        System.out.println(">>> 种子数据初始化完成！");
    }

    private Holding createHolding(String name, String code, HoldingType type,
                                   CostAlgorithm algorithm, BigDecimal shares,
                                   BigDecimal predictedDividend, String color) {
        return Holding.builder()
                .id(UUID.randomUUID().toString())
                .name(name).code(code).type(type)
                .costAlgorithm(algorithm)
                .shares(shares).cost(BigDecimal.ZERO)
                .costPerShare(BigDecimal.ZERO)
                .marketValue(BigDecimal.ZERO)
                .predictedDividend(predictedDividend)
                .dividendRate(BigDecimal.ZERO)
                .priceDividendRate(BigDecimal.ZERO)
                .totalDividendReceived(BigDecimal.ZERO)
                .netInvestment(BigDecimal.ZERO)
                .dividendRecoveryRate(BigDecimal.ZERO)
                .estimatedRecoveryYears(BigDecimal.ZERO)
                .color(color).deleted(false)
                .build();
    }

    private Transaction createTransaction(String holdingId, TransactionType type,
                                           String date, BigDecimal quantity,
                                           BigDecimal price, BigDecimal fee) {
        BigDecimal total = quantity.multiply(price).add(fee)
                .setScale(2, RoundingMode.HALF_UP);
        return Transaction.builder()
                .id(UUID.randomUUID().toString())
                .holdingId(holdingId).type(type)
                .date(LocalDate.parse(date))
                .quantity(quantity).price(price).fee(fee).total(total)
                .source("manual")
                .build();
    }

    private DividendEvent createEvent(String holdingId, String holdingName,
                                       EventType type, String date,
                                       BigDecimal amount, EventStatus status) {
        return DividendEvent.builder()
                .id(UUID.randomUUID().toString())
                .holdingId(holdingId).holdingName(holdingName)
                .type(type).date(LocalDate.parse(date))
                .amount(amount).status(status)
                .description("")
                .build();
    }

    private CoverageCategory createCategory(String name, String icon,
                                             int percentage, String color) {
        return CoverageCategory.builder()
                .id(UUID.randomUUID().toString())
                .name(name).icon(icon)
                .percentage(BigDecimal.valueOf(percentage))
                .color(color).build();
    }

    private ExchangeRate createRate(String pair, String label, BigDecimal rate) {
        return ExchangeRate.builder()
                .id(UUID.randomUUID().toString())
                .pair(pair).label(label).rate(rate)
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
