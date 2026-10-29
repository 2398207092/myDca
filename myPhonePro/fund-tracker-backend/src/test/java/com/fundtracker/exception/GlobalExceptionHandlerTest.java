package com.fundtracker.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-23：错误语义收口——请求体不可读 / 参数类型不匹配应返回 HTTP 400，
 * 而不是掉进兜底 handler 变成 500。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("P2-23: 请求体不可读（非法 JSON/枚举/日期格式）→ HTTP 400「请求体格式错误」")
    void handleMessageNotReadable_returns400() {
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("bad body", (org.springframework.http.HttpInputMessage) null);

        ResponseEntity<Map<String, Object>> resp = handler.handleMessageNotReadable(ex);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals(400, resp.getBody().get("code"));
        assertEquals("请求体格式错误", resp.getBody().get("message"));
        assertTrue(resp.getBody().containsKey("data"));
        assertNull(resp.getBody().get("data"));
    }

    @Test
    @DisplayName("P2-23: 路径/查询参数类型不匹配 → HTTP 400「参数类型错误」")
    void handleTypeMismatch_returns400() {
        MethodArgumentTypeMismatchException ex =
                new MethodArgumentTypeMismatchException("id", Integer.class, "abc", null, null);

        ResponseEntity<Map<String, Object>> resp = handler.handleTypeMismatch(ex);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals(400, resp.getBody().get("code"));
        assertEquals("参数类型错误", resp.getBody().get("message"));
        assertTrue(resp.getBody().containsKey("data"));
        assertNull(resp.getBody().get("data"));
    }
}
