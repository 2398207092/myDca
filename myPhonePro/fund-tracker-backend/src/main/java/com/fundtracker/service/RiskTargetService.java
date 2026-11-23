package com.fundtracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.RiskTargetDTO;
import com.fundtracker.model.dto.UpdateRiskTargetReq;
import com.fundtracker.model.entity.UserRiskTarget;
import com.fundtracker.repository.UserRiskTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.Map;

/**
 * 风险资产目标配置的云端存储（每用户一条）。
 * 不在 user_settings 里存：生产是多用户，user_settings 目前是全局单行。
 */
@Service
@RequiredArgsConstructor
public class RiskTargetService {

    private static final int MAX_JSON_LENGTH = 2000;

    private final UserRiskTargetRepository repository;
    private final ObjectMapper objectMapper;

    public RiskTargetDTO get(String userId) {
        requireUser(userId);
        return repository.findByUserId(userId)
                .map(this::toDto)
                .orElseGet(() -> RiskTargetDTO.builder().riskTargetJson(null).build());
    }

    @Transactional
    public RiskTargetDTO save(String userId, UpdateRiskTargetReq req) {
        requireUser(userId);
        String json = req == null ? null : req.getRiskTargetJson();
        validate(json);

        UserRiskTarget entity = repository.findByUserId(userId)
                .orElseGet(() -> UserRiskTarget.builder().userId(userId).build());
        entity.setRiskTargetJson(json);
        return toDto(repository.save(entity));
    }

    private void requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(401, "未认证");
        }
    }

    private RiskTargetDTO toDto(UserRiskTarget entity) {
        return RiskTargetDTO.builder()
                .riskTargetJson(entity.getRiskTargetJson())
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null)
                .build();
    }

    private void validate(String json) {
        if (json == null || json.isBlank()) {
            throw new BusinessException(400, "目标配置不能为空");
        }
        if (json.length() > MAX_JSON_LENGTH) {
            throw new BusinessException(400, "目标配置过长");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "目标配置格式错误");
        }
        if (root == null || !root.isObject()) {
            throw new BusinessException(400, "目标配置格式错误");
        }

        String mode = root.path("mode").asText(null);
        if (!"A".equals(mode) && !"B".equals(mode)) {
            throw new BusinessException(400, "目标模式必须是 A 或 B");
        }

        JsonNode targets = root.path("targets");
        if (!targets.isObject() || targets.isEmpty()) {
            throw new BusinessException(400, "目标比例不能为空");
        }

        double sum = 0;
        int count = 0;
        Iterator<Map.Entry<String, JsonNode>> fields = targets.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode value = entry.getValue();
            if (!value.isNumber()) {
                throw new BusinessException(400, "目标比例必须是数字");
            }
            double pct = value.asDouble();
            if (pct < 0 || pct > 100) {
                throw new BusinessException(400, "目标比例必须在 0-100 之间");
            }
            sum += pct;
            count++;
        }
        if (count == 0 || Math.abs(sum - 100) > 0.01) {
            throw new BusinessException(400, "目标比例合计必须为 100%");
        }
    }
}