package com.fundtracker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 交易日历工具类
 * 根据节假日配置和周末规则判断指定市场的交易日。
 * 市场类型：china（A股/内地基金）、us（美股/QDII）、crypto（365天交易）
 */
@Slf4j
@Component
public class TradingCalendar {

    private final Map<String, Set<String>> holidays;
    private static final String HOLIDAYS_FILE = "holidays.json";

    public TradingCalendar() {
        this.holidays = loadHolidays();
    }

    private Map<String, Set<String>> loadHolidays() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = new ClassPathResource(HOLIDAYS_FILE).getInputStream();
            Map<String, Set<String>> raw = mapper.readValue(is, new TypeReference<>() {});
            log.info("交易日历加载成功: china={}个节假日, us={}个节假日",
                    raw.getOrDefault("china", Collections.emptySet()).size(),
                    raw.getOrDefault("us", Collections.emptySet()).size());
            return raw;
        } catch (Exception e) {
            log.warn("加载节假日配置失败: {}", e.getMessage());
            return Map.of("china", Set.of(), "us", Set.of());
        }
    }

    /**
     * 判断指定日期是否为指定市场的交易日
     */
    public boolean isTradingDay(LocalDate date, String market) {
        if ("crypto".equals(market)) {
            return true;
        }
        // 周末非交易日
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        // 节假日非交易日
        Set<String> marketHolidays = holidays.getOrDefault(market, Collections.emptySet());
        return !marketHolidays.contains(date.toString());
    }

    /**
     * 返回指定日期起第一个交易日（含date本身）
     */
    public LocalDate nextTradingDay(LocalDate date, String market) {
        LocalDate d = date;
        while (!isTradingDay(d, market)) {
            d = d.plusDays(1);
        }
        return d;
    }

    /**
     * 返回指定日期前最近一个交易日（含date本身）
     */
    public LocalDate previousTradingDay(LocalDate date, String market) {
        LocalDate d = date;
        while (!isTradingDay(d, market)) {
            d = d.minusDays(1);
        }
        return d;
    }

    /**
     * 计算指定月份的交易天数
     */
    public int countTradingDaysInMonth(int year, int month, String market) {
        if ("crypto".equals(market)) {
            return LocalDate.of(year, month, 1).lengthOfMonth();
        }
        int count = 0;
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (isTradingDay(d, market)) {
                count++;
            }
        }
        return count;
    }
}
