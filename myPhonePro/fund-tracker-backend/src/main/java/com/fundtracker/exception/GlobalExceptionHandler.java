package com.fundtracker.exception;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> body = new HashMap<>();
        Map<String, String[]> errors = new HashMap<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            String field = fieldError.getField();
            errors.put(field, new String[]{fieldError.getDefaultMessage()});
        }

        body.put("code", 400);
        body.put("message", "参数错误");
        body.put("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    /** 乐观锁冲突：并发写同一行时，后到者明确报错而非静默覆盖（配合实体 @Version） */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("乐观锁冲突（并发写竞争）: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("code", 4090);
        body.put("message", "数据刚被其他操作修改，请刷新后重试");
        body.put("data", null);
        return ResponseEntity.status(HttpStatus.OK).body(body);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", ex.getCode());
        body.put("message", ex.getMessage());
        body.put("data", null);
        return ResponseEntity.status(HttpStatus.OK).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnknown(Exception ex) {
        log.error("未处理的异常", ex);
        Map<String, Object> body = new HashMap<>();
        body.put("code", 500);
        body.put("message", "服务器内部错误，请稍后重试");
        body.put("data", null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
