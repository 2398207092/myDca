package com.fundtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundtracker.model.dto.HoldingSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FundSearchService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<HoldingSearchResult> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }

        List<HoldingSearchResult> results = new ArrayList<>();

        try {
            String encoded = URLEncoder.encode(keyword, "UTF-8");
            String urlStr = "https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx?m=1&key=" + encoded;

            URI uri = new URI(urlStr);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "*/*");

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                log.warn("天天基金API返回状态码: {}", responseCode);
                return results;
            }

            // 天天基金API返回UTF-8编码的JSON数据
            StringBuilder sb = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            conn.disconnect();

            String response = sb.toString();
            results.addAll(parseWithJackson(response));
        } catch (Exception e) {
            log.warn("天天基金搜索失败: {}", e.getMessage(), e);
        }

        return results;
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
