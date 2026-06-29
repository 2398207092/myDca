package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.*;
import com.fundtracker.model.entity.ManualAsset;
import com.fundtracker.repository.ManualAssetRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualAssetService {

    private final ManualAssetRepository manualAssetRepository;

    public List<ManualAssetDTO> listManualAssets() {
        return manualAssetRepository.findAllByOrderByTypeAscAmountDesc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public ManualAssetDTO getManualAsset(String id) {
        ManualAsset asset = manualAssetRepository.findById(id)
                .orElseThrow(() -> new BusinessException(6001, "手动资产不存在"));
        return toDTO(asset);
    }

    @Transactional
    public ManualAssetDTO createManualAsset(CreateManualAssetReq req) {
        // 如果没有现金资产，自动标记为主账户
        boolean isPrimary = "cash".equals(req.getType())
                && manualAssetRepository.findByType("cash").isEmpty();

        ManualAsset asset = ManualAsset.builder()
                .name(req.getName())
                .type(req.getType())
                .amount(req.getAmount())
                .currency(req.getCurrency() != null ? req.getCurrency() : "CNY")
                .note(req.getNote())
                .isPrimary(isPrimary)
                .build();
        asset = manualAssetRepository.save(asset);
        log.info("创建手动资产: {} [{}] ¥{}", asset.getName(), asset.getType(), asset.getAmount());
        return toDTO(asset);
    }

    @Transactional
    public ManualAssetDTO updateManualAsset(String id, UpdateManualAssetReq req) {
        ManualAsset asset = manualAssetRepository.findById(id)
                .orElseThrow(() -> new BusinessException(6001, "手动资产不存在"));

        if (req.getName() != null) asset.setName(req.getName());
        if (req.getType() != null) asset.setType(req.getType());
        if (req.getAmount() != null) asset.setAmount(req.getAmount());
        if (req.getCurrency() != null) asset.setCurrency(req.getCurrency());
        if (req.getNote() != null) asset.setNote(req.getNote());

        asset = manualAssetRepository.save(asset);
        log.info("更新手动资产: {} [{}] ¥{}", asset.getName(), asset.getType(), asset.getAmount());
        return toDTO(asset);
    }

    @Transactional
    public void deleteManualAsset(String id) {
        ManualAsset asset = manualAssetRepository.findById(id)
                .orElseThrow(() -> new BusinessException(6001, "手动资产不存在"));
        manualAssetRepository.delete(asset);
        log.info("删除手动资产: {} [{}]", asset.getName(), asset.getType());
    }

    public BigDecimal getTotalByType(String type) {
        List<ManualAsset> assets = manualAssetRepository.findByType(type);
        return assets.stream()
                .map(ManualAsset::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 调整现金余额。自动找到主现金资产进行操作。
     * 被 @Transactional 调用方使用，异常会触发事务回滚。
     *
     * @param holdingId 交易的 holdingId（仅用于日志）
     * @param amount    正数=增加现金，负数=减少现金
     */
    public void adjustCash(String holdingId, BigDecimal amount) {
        List<ManualAsset> cashAssets = manualAssetRepository.findByType("cash");
        if (cashAssets.isEmpty()) {
            log.warn("adjustCash: 无现金资产，跳过调整 (holdingId={}, amount={})", holdingId, amount);
            return;
        }
        // 选主账户
        ManualAsset target = cashAssets.stream()
                .filter(ManualAsset::isPrimary)
                .findFirst()
                .orElse(cashAssets.get(0));
        BigDecimal newAmount = target.getAmount().add(amount);
        target.setAmount(newAmount);
        manualAssetRepository.save(target);
        log.info("adjustCash: {} 现金调整 {} → {} (变动: {})",
                target.getName(), newAmount.subtract(amount), newAmount, amount);
    }

    /**
     * 启动时初始化：如果已存在现金资产但没有 isPrimary 标记，自动标记第一个
     */
    @PostConstruct
    public void initPrimaryFlag() {
        List<ManualAsset> cashAssets = manualAssetRepository.findByType("cash");
        boolean hasPrimary = cashAssets.stream().anyMatch(ManualAsset::isPrimary);
        if (!hasPrimary && !cashAssets.isEmpty()) {
            ManualAsset first = cashAssets.get(0);
            first.setPrimary(true);
            manualAssetRepository.save(first);
            log.info("自动标记现金资产 [{}] 为主账户", first.getName());
        }
    }

    private ManualAssetDTO toDTO(ManualAsset asset) {
        return ManualAssetDTO.builder()
                .id(asset.getId())
                .name(asset.getName())
                .type(asset.getType())
                .amount(asset.getAmount())
                .currency(asset.getCurrency())
                .note(asset.getNote())
                .isPrimary(asset.isPrimary())
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .build();
    }
}
