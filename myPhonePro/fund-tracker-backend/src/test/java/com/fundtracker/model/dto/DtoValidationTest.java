package com.fundtracker.model.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 请求 DTO 校验注解生效性测试（P1-18：@Valid 背后的注解必须是活的）
 */
class DtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("UpdateHoldingReq：负份额被拦截（@Valid 已在 Controller 激活）")
    void updateHoldingReq_negativeShares() {
        UpdateHoldingReq req = new UpdateHoldingReq();
        req.setShares(new BigDecimal("-1"));

        Set<jakarta.validation.ConstraintViolation<UpdateHoldingReq>> violations = validator.validate(req);

        assertFalse(violations.isEmpty(), "负份额必须被 @Positive 拦截");
    }

    @Test
    @DisplayName("UpdateHoldingReq：合法值与 null（部分更新）均放行")
    void updateHoldingReq_validAndNullPass() {
        UpdateHoldingReq partial = new UpdateHoldingReq();
        partial.setName("只改名");
        assertTrue(validator.validate(partial).isEmpty(), "null 字段应放行（部分更新语义）");

        UpdateHoldingReq valid = new UpdateHoldingReq();
        valid.setShares(new BigDecimal("100"));
        valid.setCostPerShare(new BigDecimal("2.5"));
        assertTrue(validator.validate(valid).isEmpty());
    }

    @Test
    @DisplayName("CreateManualAssetReq：负金额被拦截（P1-18：此前可写入负现金资产）")
    void createManualAssetReq_negativeAmount() {
        CreateManualAssetReq req = new CreateManualAssetReq();
        req.setName("测试");
        req.setType("cash");
        req.setAmount(new BigDecimal("-100"));

        Set<jakarta.validation.ConstraintViolation<CreateManualAssetReq>> violations = validator.validate(req);

        assertFalse(violations.isEmpty(), "负金额必须被 @PositiveOrZero 拦截");
    }

    @Test
    @DisplayName("CreateManualAssetReq：crypto 场景 amount=null 仍放行（金额由后端折算）")
    void createManualAssetReq_nullAmountPass() {
        CreateManualAssetReq req = new CreateManualAssetReq();
        req.setName("USDT");
        req.setType("crypto");
        req.setQuantity(new BigDecimal("84.14"));

        assertTrue(validator.validate(req).isEmpty(), "null amount 应放行（crypto 折算语义）");
    }
}
