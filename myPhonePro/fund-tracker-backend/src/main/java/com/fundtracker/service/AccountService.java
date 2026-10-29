package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.*;
import com.fundtracker.model.entity.Account;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.entity.ManualAsset;
import com.fundtracker.repository.AccountRepository;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.repository.ManualAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 资产账户服务。
 * 账户是「一组资产」的容器：一个账户可同时持有现金（CNY/USDT）与多种加密币。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final Set<String> VALID_PURPOSES = Set.of("daily", "invest", "exchange", "other");
    private static final Set<String> VALID_CURRENCIES = Set.of("CNY", "USDT");

    private final AccountRepository accountRepository;
    private final ManualAssetRepository manualAssetRepository;
    private final HoldingRepository holdingRepository;

    /**
     * 列出账户及其资产。没有账户时自动建一个「默认账户」，
     * 并把历史遗留（accountId 为空）的资产挂进去。
     */
    @Transactional
    public List<AccountDTO> listAccounts(String userId) {
        normalizeDefaults(userId);

        List<Account> accounts = accountRepository.findByUserIdAndDeletedFalseOrderBySortOrderAsc(userId);
        if (accounts.isEmpty()) {
            accounts = List.of(ensureDefaultAccount(userId));
        }

        Map<String, List<ManualAsset>> byAccount = manualAssetRepository
                .findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(a -> a.getAccountId() != null)
                .collect(Collectors.groupingBy(ManualAsset::getAccountId));

        // P1-36 修复：账户视图纳入归属持仓市值（此前 totalValueCny 只算手动资产，
        // 账户页总额比真实少 44%）；持仓 market_value 为折算后人民币口径，可直接相加
        Map<String, List<Holding>> holdingsByAccount = holdingRepository
                .findByUserIdAndDeletedFalse(userId).stream()
                .filter(h -> h.getAccountId() != null)
                .collect(Collectors.groupingBy(Holding::getAccountId));

        return accounts.stream()
                .map(a -> toDTO(a,
                        byAccount.getOrDefault(a.getId(), List.of()),
                        holdingsByAccount.getOrDefault(a.getId(), List.of())))
                .collect(Collectors.toList());
    }

    /**
     * 收敛默认账户：保证「有且只有一个」。
     * 历史数据或并发写入可能产生 0 个或多个 default，这里每次读取时纠正一次。
     */
    @Transactional
    public void normalizeDefaults(String userId) {
        List<Account> accounts = accountRepository.findByUserIdAndDeletedFalseOrderBySortOrderAsc(userId);
        if (accounts.isEmpty()) return;

        List<Account> defaults = accounts.stream().filter(Account::isDefault).toList();
        if (defaults.size() == 1) return;

        Account keep = defaults.isEmpty() ? accounts.get(0) : defaults.get(0);
        for (Account a : accounts) {
            boolean should = a.getId().equals(keep.getId());
            if (a.isDefault() != should) {
                a.setDefault(should);
                accountRepository.save(a);
            }
        }
        if (defaults.size() > 1) {
            log.warn("用户 {} 存在 {} 个默认账户，已收敛为「{}」", userId, defaults.size(), keep.getName());
        }
    }

    /**
     * 保证用户至少有一个默认账户；同时把 accountId 为空的历史资产归入该账户。
     */
    @Transactional
    public Account ensureDefaultAccount(String userId) {
        Optional<Account> existing = accountRepository.findDefault(userId);
        if (existing.isPresent()) {
            adoptOrphans(userId, existing.get().getId());
            return existing.get();
        }

        Account account = Account.builder()
                .userId(userId)
                .name("默认账户")
                .purpose("other")
                .baseCurrency("CNY")
                .isDefault(true)
                .sortOrder(0)
                .deleted(false)
                .build();
        account = accountRepository.save(account);
        adoptOrphans(userId, account.getId());
        log.info("已为用户 {} 创建默认账户 {}", userId, account.getId());
        return account;
    }

    private void adoptOrphans(String userId, String accountId) {
        List<ManualAsset> orphans = manualAssetRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(a -> a.getAccountId() == null)
                .toList();
        for (ManualAsset a : orphans) {
            a.setAccountId(accountId);
            manualAssetRepository.save(a);
        }
        if (!orphans.isEmpty()) {
            log.info("已将 {} 条未归属资产挂到默认账户 {}", orphans.size(), accountId);
        }
    }

    @Transactional
    public AccountDTO createAccount(CreateAccountReq req, String userId) {
        String name = req.getName() == null ? "" : req.getName().trim();
        if (name.isEmpty()) {
            throw BusinessException.invalidParam("账户名称不能为空");
        }
        if (accountRepository.existsByUserIdAndNameAndDeletedFalse(userId, name)) {
            throw BusinessException.invalidParam("账户名称已存在: " + name);
        }

        String purpose = normalizePurpose(req.getPurpose());
        String currency = normalizeCurrency(req.getBaseCurrency());
        boolean first = accountRepository.countActive(userId) == 0;

        Account account = Account.builder()
                .userId(userId)
                .name(name)
                .purpose(purpose)
                .baseCurrency(currency)
                .isDefault(first)
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : (int) accountRepository.countActive(userId))
                .deleted(false)
                .build();
        account = accountRepository.save(account);
        log.info("创建账户: {} [{} / {}] userId={}", name, purpose, currency, userId);
        return toDTO(account, List.of(), List.of());
    }

    @Transactional
    public AccountDTO updateAccount(String id, UpdateAccountReq req, String userId) {
        Account account = accountRepository.findByIdAndUserIdAndDeletedFalse(id, userId)
                .orElseThrow(() -> BusinessException.invalidParam("账户不存在"));

        if (req.getName() != null) {
            String name = req.getName().trim();
            if (name.isEmpty()) throw BusinessException.invalidParam("账户名称不能为空");
            if (!name.equals(account.getName())
                    && accountRepository.existsByUserIdAndNameAndDeletedFalse(userId, name)) {
                throw BusinessException.invalidParam("账户名称已存在: " + name);
            }
            account.setName(name);
        }
        String newPurpose = req.getPurpose() != null ? normalizePurpose(req.getPurpose()) : account.getPurpose();
        String newCurrency = req.getBaseCurrency() != null ? normalizeCurrency(req.getBaseCurrency()) : account.getBaseCurrency();
        // 用途联动本位币：交易所账户必须 USDT 本位币（与前端联动同一条规则）
        if ("exchange".equals(newPurpose) && !"USDT".equals(newCurrency)) {
            throw BusinessException.invalidParam("交易所账户的本位币必须是 USDT，请先调整本位币或用途");
        }
        account.setPurpose(newPurpose);
        account.setBaseCurrency(newCurrency);
        if (req.getSortOrder() != null) account.setSortOrder(req.getSortOrder());

        if (Boolean.TRUE.equals(req.getIsDefault()) && !account.isDefault()) {
            clearOtherDefaults(userId, account.getId());
            account.setDefault(true);
        }

        // 本位币变更校验：账户下已有资产必须与新本位币匹配，否则这些资产会从所有匹配校验中漏掉
        if (req.getBaseCurrency() != null && !normalizeCurrency(req.getBaseCurrency()).equals(account.getBaseCurrency())) {
            throw BusinessException.invalidParam("账户本位币不可直接修改（账户下已有资产），如需调整请先移走该账户下的资产");
        }
        account = accountRepository.save(account);
        List<ManualAsset> assets = manualAssetRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(a -> id.equals(a.getAccountId()))
                .toList();
        List<Holding> holdings = holdingRepository.findByUserIdAndDeletedFalse(userId).stream()
                .filter(h -> id.equals(h.getAccountId()))
                .toList();
        return toDTO(account, assets, holdings);
    }

    @Transactional
    public void deleteAccount(String id, String userId) {
        Account account = accountRepository.findByIdAndUserIdAndDeletedFalse(id, userId)
                .orElseThrow(() -> BusinessException.invalidParam("账户不存在"));

        long assetCount = manualAssetRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(a -> id.equals(a.getAccountId()))
                .count();
        if (assetCount > 0) {
            throw BusinessException.invalidParam("账户下还有 " + assetCount + " 项资产，请先移走或删除");
        }

        account.setDeleted(true);
        accountRepository.save(account);

        // 若删的是默认账户，把剩下的第一个设为默认
        if (account.isDefault()) {
            List<Account> rest = accountRepository.findByUserIdAndDeletedFalseOrderBySortOrderAsc(userId);
            if (!rest.isEmpty()) {
                Account next = rest.get(0);
                next.setDefault(true);
                accountRepository.save(next);
            }
        }
        log.info("删除账户: {} (userId={})", account.getName(), userId);
    }

    private void clearOtherDefaults(String userId, String keepId) {
        accountRepository.findByUserIdAndDeletedFalseOrderBySortOrderAsc(userId).stream()
                .filter(a -> !a.getId().equals(keepId) && a.isDefault())
                .forEach(a -> {
                    a.setDefault(false);
                    accountRepository.save(a);
                });
    }

    private String normalizePurpose(String raw) {
        if (raw == null || raw.isBlank()) return "other";
        if (!VALID_PURPOSES.contains(raw)) {
            throw BusinessException.invalidParam("账户用途无效: " + raw);
        }
        return raw;
    }

    private String normalizeCurrency(String raw) {
        if (raw == null || raw.isBlank()) return "CNY";
        String c = raw.toUpperCase();
        if (!VALID_CURRENCIES.contains(c)) {
            throw BusinessException.invalidParam("本位币仅支持 CNY / USDT: " + raw);
        }
        return c;
    }

    private AccountDTO toDTO(Account a, List<ManualAsset> assets, List<Holding> holdings) {
        BigDecimal total = assets.stream()
                .map(x -> x.getAmount() != null ? x.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal holdingsValue = holdings.stream()
                .map(h -> h.getMarketValue() != null ? h.getMarketValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return AccountDTO.builder()
                .id(a.getId())
                .name(a.getName())
                .purpose(a.getPurpose())
                .baseCurrency(a.getBaseCurrency())
                .isDefault(a.isDefault())
                .sortOrder(a.getSortOrder())
                .totalValueCny(total.add(holdingsValue))
                .assetCount(assets.size() + holdings.size())
                .assets(assets.stream().map(this::toAssetDTO).collect(Collectors.toList()))
                .build();
    }

    private ManualAssetDTO toAssetDTO(ManualAsset asset) {
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

    /**
     * 资产类型与账户本位币是否匹配：
     * USDT 账户（交易所）只能挂加密资产；CNY 账户只能挂人民币资产（现金/基金等）。
     * accountId 为空视为不约束。
     */
    public boolean matchesAssetType(String accountId, boolean isCryptoAsset) {
        if (accountId == null || accountId.isBlank()) {
            return true;
        }
        Account acc = accountRepository.findById(accountId).orElse(null);
        if (acc == null) {
            return false; // 账户不存在视作不匹配，由调用方决定报错文案
        }
        boolean usdtAccount = "USDT".equalsIgnoreCase(acc.getBaseCurrency());
        return isCryptoAsset == usdtAccount;
    }
}
