package com.fundtracker.service;

import com.fundtracker.common.HttpClientWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 外部接口探测测试（@Tag("probe")）
 *
 * 用途：验证项目依赖的外部 HTTP 接口仍可访问、且未被反爬策略变更拦截。
 * 这类测试依赖真实网络，默认不随 mvn test 执行（避免网络抖动干扰主流程）。
 * 手动触发：mvn test -Dgroups=probe
 *
 * 背景（2026-08-08 事故）：天天基金 FundMNBasicInformation 接口对浏览器 UA 返回反爬拦截
 * （190字节"网络繁忙"），只有 curl/ UA 能拿到数据。若此类探测测试当时存在，部署前就能发现。
 */
@ExtendWith(MockitoExtension.class)
@Tag("probe")
class ExternalApiProbeTest {

    private final HttpClientWrapper httpClient = new HttpClientWrapper();

    /** 成立日期接口 UA（实测必须 curl/，浏览器 UA 被反爬） */
    private static final Map<String, String> ESTAB_DATE_HEADERS = Map.of(
            "User-Agent", "curl/8.0");

    /** fhsp 分红页 UA（浏览器 UA） */
    private static final Map<String, String> FHSP_HEADERS = Map.of(
            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

    /** pingzhongdata 净值 JS UA（浏览器 UA） */
    private static final Map<String, String> PINGZHONG_HEADERS = Map.of(
            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

    @Test
    @DisplayName("成立日期接口可访问且含 ESTABDATE（curl/ UA）")
    void establishDateApiAccessible() {
        String url = "https://fundmobapi.eastmoney.com/FundMNewApi/FundMNBasicInformation?FCODE=007466&deviceid=Wap&plat=Wap&product=EFund&version=2.0.0";
        String body = httpClient.getText(url, ESTAB_DATE_HEADERS);

        assertNotNull(body, "成立日期接口不可达（重试耗尽）");
        assertTrue(body.contains("ESTABDATE"),
                "成立日期接口未返回 ESTABDATE，可能被反爬拦截。响应: "
                        + HttpClientWrapper.truncate(body, 300));
        assertFalse(body.contains("网络繁忙"), "成立日期接口返回反爬拦截: " + HttpClientWrapper.truncate(body, 300));
    }

    @Test
    @DisplayName("fhsp 分红页可访问且含分红表格（浏览器 UA）")
    void fhspPageAccessible() {
        String url = "https://fundf10.eastmoney.com/fhsp_007466.html";
        String body = httpClient.getText(url, FHSP_HEADERS);

        assertNotNull(body, "fhsp 分红页不可达（重试耗尽）");
        assertTrue(body.contains("派现金") || body.contains("每份分红"),
                "fhsp 页面未找到分红数据，可能被反爬或页面结构变化。响应: "
                        + HttpClientWrapper.truncate(body, 300));
    }

    @Test
    @DisplayName("pingzhongdata 净值 JS 可访问（浏览器 UA）")
    void pingZhongDataAccessible() {
        String url = "https://fund.eastmoney.com/pingzhongdata/007466.js";
        String body = httpClient.getText(url, PINGZHONG_HEADERS);

        assertNotNull(body, "pingzhongdata 净值接口不可达（重试耗尽）");
        assertTrue(body.contains("Data_netWorthTrend"),
                "净值 JS 未找到 Data_netWorthTrend，可能被反爬或结构变化。响应: "
                        + HttpClientWrapper.truncate(body, 300));
    }

    @Test
    @DisplayName("基金搜索接口可访问（浏览器 UA）")
    void fundSearchApiAccessible() {
        String url = "https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx?m=1&key=007466";
        String body = httpClient.getText(url, Map.of(
                "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                "Accept", "*/*"));

        assertNotNull(body, "基金搜索接口不可达（重试耗尽）");
        assertTrue(body.contains("Datas") || body.contains("CODE"),
                "搜索接口响应异常，可能被反爬或结构变化。响应: "
                        + HttpClientWrapper.truncate(body, 300));
    }
}
