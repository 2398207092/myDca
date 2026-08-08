package com.fundtracker.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundtracker.common.HttpClientWrapper;
import com.fundtracker.model.dto.HoldingSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 天天基金搜索数据源 Provider（#1 外部数据源层）
 *
 * 内聚：URL、UA（浏览器 UA）、JSON 解析、失败策略。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FundSearchProvider {

    private final HttpClientWrapper httpClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 天天基金搜索 API UA：实测浏览器 UA 可正常访问 */
    private static final Map<String, String> HEADERS = Map.of(
            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Accept", "*/*");

    /**
     * 按关键词搜索基金
     *
     * @return 搜索结果列表；失败返回空列表
     */
    public List<HoldingSearchResult> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String url = "https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx?m=1&key=" + encoded;
            String json = httpClient.getText(url, HEADERS);
            if (json == null) {
                log.warn("天天基金搜索接口重试 3 次后仍失败");
                return List.of();
            }
            return parseWithJackson(json);
        } catch (Exception e) {
            log.warn("天天基金搜索失败: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private List<HoldingSearchResult> parseWithJackson(String json) throws Exception {
        List<HoldingSearchResult> results = new ArrayList<>();
        JsonNode root = objectMapper.readTree(json);
        JsonNode datas = root.get("Datas");
        if (datas == null || !datas.isArray()) return results;

        for (JsonNode item : datas) {
            String code = getTextField(item, "CODE");
            String name = getTextField(item, "NAME");
            String shortName = getTextField(item, "SHORTNAME");
            String category = getTextField(item, "CATEGORYDESC");
            String pinyin = getTextField(item, "JP");

            JsonNode baseInfo = item.get("FundBaseInfo");
            if (baseInfo != null) {
                if (code == null) code = getTextField(baseInfo, "FCODE");
                if (shortName == null) shortName = getTextField(baseInfo, "SHORTNAME");
                if (name == null) name = getTextField(baseInfo, "SHORTNAME");
            }
            if (code == null) continue;
            String displayName = shortName != null ? shortName : name;
            if (displayName == null) continue;

            String type = guessType(code, displayName, category);
            results.add(HoldingSearchResult.builder()
                    .code(code).name(displayName).type(type)
                    .pinyin(pinyin != null ? pinyin : "")
                    .netWorth("--").fullName(displayName).build());
        }
        return results;
    }

    private String getTextField(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field != null && !field.isNull()) {
            String val = field.asText().trim();
            return val.isEmpty() ? null : val;
        }
        return null;
    }

    private String guessType(String code, String name, String category) {
        if (name != null && name.contains("ETF")) return "ETF";
        if (category != null && category.contains("基金")) return "fund";
        if (code != null && code.length() == 6) {
            if (code.startsWith("0") || code.startsWith("3") || code.startsWith("6")) return "A股";
        }
        if (code != null && code.length() == 5) return "港股";
        return "fund";
    }
}
