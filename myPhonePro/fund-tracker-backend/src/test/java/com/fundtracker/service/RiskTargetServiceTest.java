package com.fundtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.RiskTargetDTO;
import com.fundtracker.model.dto.UpdateRiskTargetReq;
import com.fundtracker.model.entity.UserRiskTarget;
import com.fundtracker.repository.UserRiskTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskTargetServiceTest {

    private static final String VALID_JSON =
            "{\"mode\":\"B\",\"targets\":{\"dividend\":60,\"crypto\":20,\"us_stock\":15,\"gold\":5}}";

    @Mock private UserRiskTargetRepository repository;

    private RiskTargetService service;

    @BeforeEach
    void setUp() {
        service = new RiskTargetService(repository, new ObjectMapper());
    }

    private UpdateRiskTargetReq req(String json) {
        UpdateRiskTargetReq req = new UpdateRiskTargetReq();
        req.setRiskTargetJson(json);
        return req;
    }

    @Test
    @DisplayName("未设置目标时返回空 JSON，不创建全局共享配置")
    void get_empty() {
        when(repository.findByUserId("u-1")).thenReturn(Optional.empty());

        RiskTargetDTO dto = service.get("u-1");

        assertNull(dto.getRiskTargetJson());
        assertNull(dto.getUpdatedAt());
        verify(repository).findByUserId("u-1");
    }

    @Test
    @DisplayName("保存新用户：写入该 userId，而不是共享单行")
    void save_newUser_scopedToUser() {
        when(repository.findByUserId("u-1")).thenReturn(Optional.empty());
        when(repository.save(any(UserRiskTarget.class))).thenAnswer(invocation -> {
            UserRiskTarget entity = invocation.getArgument(0);
            entity.setId("rt-1");
            entity.setUpdatedAt(LocalDateTime.now());
            return entity;
        });

        RiskTargetDTO dto = service.save("u-1", req(VALID_JSON));

        assertEquals(VALID_JSON, dto.getRiskTargetJson());
        ArgumentCaptor<UserRiskTarget> captor = ArgumentCaptor.forClass(UserRiskTarget.class);
        verify(repository).save(captor.capture());
        assertEquals("u-1", captor.getValue().getUserId());
        assertEquals(VALID_JSON, captor.getValue().getRiskTargetJson());
    }

    @Test
    @DisplayName("保存已有用户：更新同一条记录，不新建")
    void save_existingUser_updates() {
        UserRiskTarget existing = UserRiskTarget.builder()
                .id("rt-1").userId("u-1")
                .riskTargetJson("{\"mode\":\"A\",\"targets\":{\"dividend\":80,\"crypto\":10,\"us_stock\":8,\"gold\":2}}")
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();
        when(repository.findByUserId("u-1")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        RiskTargetDTO dto = service.save("u-1", req(VALID_JSON));

        assertEquals(VALID_JSON, dto.getRiskTargetJson());
        assertEquals("rt-1", existing.getId());
        verify(repository).save(existing);
    }

    @Test
    @DisplayName("两个用户各读各的，不串配置")
    void get_isIsolatedByUser() {
        UserRiskTarget u1 = UserRiskTarget.builder()
                .id("rt-1").userId("u-1").riskTargetJson(VALID_JSON).updatedAt(LocalDateTime.now()).build();
        when(repository.findByUserId("u-1")).thenReturn(Optional.of(u1));
        when(repository.findByUserId("u-2")).thenReturn(Optional.empty());

        assertEquals(VALID_JSON, service.get("u-1").getRiskTargetJson());
        assertNull(service.get("u-2").getRiskTargetJson());
        verify(repository).findByUserId("u-1");
        verify(repository).findByUserId("u-2");
    }

    @Test
    @DisplayName("目标合计不是 100% → 拒绝，不落库")
    void save_rejectsInvalidSum() {
        String bad = "{\"mode\":\"A\",\"targets\":{\"dividend\":80,\"crypto\":10,\"us_stock\":8,\"gold\":1}}";

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save("u-1", req(bad)));

        assertEquals(400, ex.getCode());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("非法 JSON / 非法模式 → 拒绝")
    void save_rejectsMalformedJson() {
        assertThrows(BusinessException.class, () -> service.save("u-1", req("{not json")));
        assertThrows(BusinessException.class,
                () -> service.save("u-1", req("{\"mode\":\"C\",\"targets\":{\"dividend\":100}}")));
        assertThrows(BusinessException.class, () -> service.save("u-1", req("")));
    }

    @Test
    @DisplayName("没有 userId（未认证）→ 401")
    void save_rejectsAnonymous() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save(null, req(VALID_JSON)));
        assertEquals(401, ex.getCode());
    }
}