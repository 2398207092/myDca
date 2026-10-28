package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.*;
import com.fundtracker.model.entity.ManualAsset;
import com.fundtracker.repository.AccountRepository;
import com.fundtracker.repository.ManualAssetRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualAssetService {

    private final ManualAssetRepository manualAssetRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final ExchangeRateService exchangeRateService;

    public List<ManualAssetDTO> listManualAssets(String userId) {
        return manualAssetRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public ManualAssetDTO getManualAsset(String id, String userId) {
        ManualAsset asset = manualAssetRepository.findById(id)
                .orElseThrow(BusinessException::manualAssetNotFound);
        if (!asset.getUserId().equals(userId)) {
            throw BusinessException.manualAssetAccessDenied();
        }
        return toDTO(asset);
    }

    @Transactional
    public ManualAssetDTO createManualAsset(CreateManualAssetReq req, String userId) {
        // 如果没有现金资产，自动标记为主账户
        boolean isPrimary = "cash".equals(req.getType())
                && manualAssetRepository.findByUserIdAndType(userId, "cash").isEmpty();

        // 归属账户：未指定则挂默认账户
        String accountId = req.getAccountId();
        if (accountId == null || accountId.isBlank()) {
            accountId = accountService.ensureDefaultAccount(userId).getId();
        } else {
            accountRepository.findByIdAndUserIdAndDeletedFalse(accountId, userId)
                    .orElseThrow(() -> BusinessException.invalidParam("账户不存在: " + req.getAccountId()));
        }
        boolean assetIsCrypto = "crypto".equals(req.getType());
        if (!accountService.matchesAssetType(accountId, assetIsCrypto)) {
            throw BusinessException.invalidParam(assetIsCrypto
                    ? "加密资产只能挂在 USDT 本位币的账户（如交易所账户）下"
                    : "人民币资产不能挂在 USDT 本位币的账户（交易所）下");
        }

        // 资产代码：未指定则按类型推断（现金 CNY / 加密资产 USDT——UI 里加密资产的数量口径就是 USDT，
        // 默认 BTC 会导致行情刷新把它按 BTC 价格估值，2026-10-28「USDT 余额 5.4 亿」事故根因）
        String assetCode = req.getAssetCode();
        if (assetCode == null || assetCode.isBlank()) {
            assetCode = "cash".equals(req.getType()) ? "CNY" : "USDT";
        }

        // 金额校验：现金必须传金额；加密资产传数量即可，金额由后端折算
        boolean isCrypto = "crypto".equals(req.getType());
        if (!isCrypto && req.getAmount() == null) {
            throw BusinessException.invalidParam("金额不能为空");
        }

        // 加密资产的记账规则：数量按 USDT 计，金额 = 数量 × 当前汇率折算成人民币。
        // 前端只传 quantity，amount 由后端统一折算，避免两边汇率口径不一致。
        BigDecimal amount = req.getAmount();
        BigDecimal quantity = req.getQuantity();
        BigDecimal unitPrice = req.getUnitPrice();
        if (isCrypto && quantity != null) {
            BigDecimal usdCny = usdCnyRate();
            if (usdCny == null) {
                throw BusinessException.bizError("汇率获取失败，无法折算加密资产金额，请稍后重试");
            }
            unitPrice = unitPrice != null ? unitPrice : usdCny;
            amount = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        }

        ManualAsset asset = ManualAsset.builder()
                .userId(userId)
                .name(req.getName())
                .type(req.getType())
                .amount(amount)
                .accountId(accountId)
                .assetCode(assetCode.toUpperCase())
                .quantity(quantity)
                .unitPrice(unitPrice)
                .priceSource(req.getPriceSource() != null ? req.getPriceSource() : "manual")
                .priceUpdatedAt(req.getUnitPrice() != null ? java.time.LocalDateTime.now() : null)
                .currency(req.getCurrency() != null ? req.getCurrency() : "CNY")
                .note(req.getNote())
                .isPrimary(isPrimary)
                .build();
        asset = manualAssetRepository.save(asset);
        log.info("创建手动资产: {} [{}] ¥{}", asset.getName(), asset.getType(), asset.getAmount());
        return toDTO(asset);
    }

    /** USD/CNY 汇率；取不到时返回 null，由调用方决定兜底 */
    private BigDecimal usdCnyRate() {
        return exchangeRateService.listAll().stream()
                .filter(r -> "USD/CNY".equals(r.getPair()))
                .map(ExchangeRateDTO::getRate)
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public ManualAssetDTO updateManualAsset(String id, UpdateManualAssetReq req, String userId) {
        ManualAsset asset = manualAssetRepository.findById(id)
                .orElseThrow(BusinessException::manualAssetNotFound);
        if (!asset.getUserId().equals(userId)) {
            throw BusinessException.manualAssetAccessDenied();
        }

        if (req.getName() != null) asset.setName(req.getName());
        if (req.getType() != null) {
            asset.setType(req.getType());
            // 类型变更（cash↔crypto）后必须重新校验账户本位币匹配，否则可能把加密资产留在 CNY 账户
            if (!accountService.matchesAssetType(asset.getAccountId(), "crypto".equals(asset.getType()))) {
                throw BusinessException.invalidParam("资产类型与账户本位币不匹配（USDT 账户只能挂加密资产），请同时更换归属账户");
            }
        }
        if (req.getAmount() != null) asset.setAmount(req.getAmount());
        if (req.getCurrency() != null) asset.setCurrency(req.getCurrency());
        if (req.getNote() != null) asset.setNote(req.getNote());

        // 资产必须归属账户（与持仓/定投同一条原则）：空值一律拒绝，不允许清除归属
        if (req.getAccountId() == null || req.getAccountId().isBlank()) {
            throw BusinessException.invalidParam("资产必须归属账户，请选择归属账户");
        }
        {
            accountRepository.findByIdAndUserIdAndDeletedFalse(req.getAccountId(), userId)
                    .orElseThrow(() -> BusinessException.invalidParam("账户不存在: " + req.getAccountId()));
            if (!accountService.matchesAssetType(req.getAccountId(), "crypto".equals(asset.getType()))) {
                throw BusinessException.invalidParam("资产类型与账户本位币不匹配（USDT 账户只能挂加密资产）");
            }
            asset.setAccountId(req.getAccountId());
        }
        if (req.getAssetCode() != null) asset.setAssetCode(req.getAssetCode().toUpperCase());
        if (req.getQuantity() != null) {
            asset.setQuantity(req.getQuantity());
            // 加密资产编辑数量时，金额按当前汇率重算（记录层 USDT，汇总层人民币）
            if ("crypto".equals(asset.getType())) {
                BigDecimal usdCny = usdCnyRate();
                if (usdCny == null) {
                    throw BusinessException.bizError("汇率获取失败，无法折算加密资产金额，请稍后重试");
                }
                asset.setUnitPrice(usdCny);
                asset.setAmount(req.getQuantity().multiply(usdCny).setScale(2, RoundingMode.HALF_UP));
                asset.setPriceUpdatedAt(java.time.LocalDateTime.now());
            }
        }
        if (req.getUnitPrice() != null) {
            asset.setUnitPrice(req.getUnitPrice());
            asset.setPriceUpdatedAt(java.time.LocalDateTime.now());
        }
        if (req.getPriceSource() != null) asset.setPriceSource(req.getPriceSource());

        asset = manualAssetRepository.save(asset);
        log.info("更新手动资产: {} [{}] ¥{}", asset.getName(), asset.getType(), asset.getAmount());
        return toDTO(asset);
    }

    @Transactional
    public void deleteManualAsset(String id, String userId) {
        ManualAsset asset = manualAssetRepository.findById(id)
                .orElseThrow(BusinessException::manualAssetNotFound);
        if (!asset.getUserId().equals(userId)) {
            throw BusinessException.manualAssetAccessDenied();
        }
        manualAssetRepository.delete(asset);
        log.info("删除手动资产: {} [{}]", asset.getName(), asset.getType());
    }

    public BigDecimal getTotalByType(String type, String userId) {
        List<ManualAsset> assets = manualAssetRepository.findByUserIdAndType(userId, type);
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
     * @param userId    用户 ID
     */
    public void adjustCash(String holdingId, BigDecimal amount, String userId) {
        adjustCash(holdingId, null, amount, userId);
    }

    /**
     * 调整现金余额。
     * @param accountId 持仓归属账户；非空时只调整该账户下的现金资产，为空时退回主现金（历史行为）
     */
    public void adjustCash(String holdingId, String accountId, BigDecimal amount, String userId) {
        List<ManualAsset> cashAssets = (accountId != null && !accountId.isBlank())
                ? manualAssetRepository.findByUserIdAndAccountId(userId, accountId).stream()
                        .filter(a -> "cash".equals(a.getType()))
                        .collect(java.util.stream.Collectors.toList())
                : manualAssetRepository.findByUserIdAndType(userId, "cash");
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
                .accountId(asset.getAccountId())
                .assetCode(asset.getAssetCode())
                .quantity(asset.getQuantity())
                .unitPrice(asset.getUnitPrice())
                .priceSource(asset.getPriceSource())
                .priceUpdatedAt(asset.getPriceUpdatedAt())
                .currency(asset.getCurrency())
                .note(asset.getNote())
                .isPrimary(asset.isPrimary())
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .build();
    }
}
