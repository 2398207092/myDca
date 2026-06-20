package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.*;
import com.fundtracker.model.entity.ManualAsset;
import com.fundtracker.repository.ManualAssetRepository;
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
        ManualAsset asset = ManualAsset.builder()
                .name(req.getName())
                .type(req.getType())
                .amount(req.getAmount())
                .currency(req.getCurrency() != null ? req.getCurrency() : "CNY")
                .note(req.getNote())
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

    private ManualAssetDTO toDTO(ManualAsset asset) {
        return ManualAssetDTO.builder()
                .id(asset.getId())
                .name(asset.getName())
                .type(asset.getType())
                .amount(asset.getAmount())
                .currency(asset.getCurrency())
                .note(asset.getNote())
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .build();
    }
}
