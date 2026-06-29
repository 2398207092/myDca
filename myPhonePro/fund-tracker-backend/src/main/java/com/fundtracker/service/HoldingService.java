package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.*;
import com.fundtracker.model.entity.DividendEvent;
import com.fundtracker.model.entity.FundDividendRecord;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.entity.Transaction;
import com.fundtracker.model.enums.CostAlgorithm;
import com.fundtracker.model.enums.EventStatus;
import com.fundtracker.model.enums.HoldingType;
import com.fundtracker.model.enums.TransactionType;
import com.fundtracker.repository.DividendEventRepository;
import com.fundtracker.repository.FundDividendRecordRepository;
import com.fundtracker.repository.HoldingRepository;
import com.fundtracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingService {

    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;
    private final CostCalculator costCalculator;
    private final FundDividendScrapeService fundDividendScrapeService;
    private final FundNavScrapeService fundNavScrapeService;
    private final FundDividendRecordRepository fundDividendRecordRepository;
    private final DividendEventRepository dividendEventRepository;

    public List<HoldingDTO> listHoldings(String type, String keyword) {
        List<Holding> holdings;
        if (type != null && !type.isEmpty()) {
            holdings = holdingRepository.findByTypeAndDeletedFalse(type);
        } else if (keyword != null && !keyword.isEmpty()) {
            holdings = holdingRepository.findByNameContainingOrCodeContainingAndDeletedFalse(keyword, keyword);
        } else {
            holdings = holdingRepository.findByDeletedFalseOrderByMarketValueDesc();
        }
        return holdings.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public HoldingDTO getHolding(String id) {
        Holding holding = holdingRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(BusinessException::holdingNotFound);
        
        // 对于基金/ETF类型，增量更新净值到数据库
        if (holding.getType() == HoldingType.fund || holding.getType() == HoldingType.ETF) {
            refreshMarketValue(holding);
        }
        
        // 计算预测年分红（基于基金历史分红记录）
        calculatePredictedDividend(holding);
        
        // 计算累计已获分红（基于已到账分红事件）
        calculateTotalDividendReceived(holding);
        
        // 重新计算衍生指标（成本息率、回本进度等），skipCostRecalc=true 避免覆盖用户手动设置的成本
        recalculateHoldingMetrics(holding, true);
        
        holding = holdingRepository.save(holding);
        
        return toDTO(holding);
    }
    
    /**
     * 刷新持仓市值（从数据库获取最新净值，若数据库无数据则触发抓取）
     */
    private void refreshMarketValue(Holding holding) {
        // 增量更新（抓取最新一条保存到数据库）
        FundNavScrapeService.LatestNavResult result = fundNavScrapeService.incrementalUpdate(holding.getCode());
        
        // 如果增量更新没返回结果，尝试从数据库直接读取
        if (result == null) {
            result = fundNavScrapeService.getLatestNavFromDb(holding.getCode());
        }
        
        // 如果数据库也没有数据，则触发首次全量抓取
        if (result == null) {
            log.info("基金 {} 净值数据为空，触发首次拉取", holding.getCode());
            result = fundNavScrapeService.fetchAndSaveNavRecords(holding.getCode());
        }
        
        if (result != null && result.unitNav() != null) {
            BigDecimal latestPrice = result.unitNav();
            LocalDate priceDate = result.navDate();
            
            // 只在净值日期更新时才刷新
            LocalDate currentPriceDate = holding.getPriceDate();
            if (currentPriceDate == null || priceDate.isAfter(currentPriceDate)) {
                holding.setLatestPrice(latestPrice);
                holding.setPriceDate(priceDate);
                
                // 计算新的市值 = 份额 × 最新净值
                BigDecimal newMarketValue = holding.getShares()
                        .multiply(latestPrice)
                        .setScale(2, RoundingMode.HALF_UP);
                holding.setMarketValue(newMarketValue);
                
                holdingRepository.save(holding);
                log.info("刷新持仓 {}({}) 市值成功: ¥{}", holding.getName(), holding.getCode(), newMarketValue);
            }
        } else {
            log.warn("刷新持仓 {}({}) 市值失败: 未获取到净值数据", holding.getName(), holding.getCode());
        }
    }

    @Transactional
    public HoldingDTO createHolding(CreateHoldingReq req) {
        // 检查代码是否已存在
        if (holdingRepository.existsByCodeAndDeletedFalse(req.getCode())) {
            throw BusinessException.holdingCodeExists();
        }

        CostAlgorithm algorithm = req.getCostAlgorithm() != null ?
                CostAlgorithm.valueOf(req.getCostAlgorithm()) : CostAlgorithm.diluted;

        BigDecimal shares = req.getShares();
        BigDecimal costPerShare = req.getCost();  // 用户输入的是每份成本

        // 总成本 = 份额 × 每份成本
        BigDecimal totalCost = shares.multiply(costPerShare).setScale(2, RoundingMode.HALF_UP);

        // 初始市值 = 总成本（创建时假设市值等于成本）
        BigDecimal marketValue = totalCost;

        // 生成随机颜色
        String[] colors = {"#FF7A45", "#4CAF50", "#2196F3", "#9C27B0", "#FF9800", "#E91E63", "#00BCD4"};
        String color = colors[(int)(Math.random() * colors.length)];

        Holding holding = Holding.builder()
                .id(UUID.randomUUID().toString())
                .name(req.getName())
                .code(req.getCode())
                .type(HoldingType.valueOf(req.getType()))
                .costAlgorithm(algorithm)
                .shares(shares)
                .costPerShare(costPerShare)
                .cost(totalCost)
                .marketValue(marketValue)
                .predictedDividend(BigDecimal.ZERO)
                .dividendRate(BigDecimal.ZERO)
                .priceDividendRate(BigDecimal.ZERO)
                .totalDividendReceived(BigDecimal.ZERO)
                .netInvestment(totalCost)
                .dividendRecoveryRate(BigDecimal.ZERO)
                .estimatedRecoveryYears(BigDecimal.ZERO)
                .reinvestRecoveryYears(BigDecimal.ZERO)
                .color(color)
                .assetCategory(req.getAssetCategory())
                .deleted(false)
                .build();

        holding = holdingRepository.save(holding);

        // 创建持仓后触发分红数据抓取
        try {
            fundDividendScrapeService.scrapeAndSave(holding.getCode());
        } catch (Exception e) {
            log.warn("创建持仓后抓取分红数据失败: {}", e.getMessage());
        }

        // 创建持仓后触发净值数据抓取（最近5年）
        try {
            fundNavScrapeService.fetchIfEmpty(holding.getCode());
        } catch (Exception e) {
            log.warn("创建持仓后抓取净值数据失败: {}", e.getMessage());
        }

        // 创建初始买入交易记录
        try {
            BigDecimal price = costPerShare; // 买入单价 = 每份成本
            BigDecimal total = shares.multiply(price).setScale(2, RoundingMode.HALF_UP);
            Transaction initTx = Transaction.builder()
                    .id(UUID.randomUUID().toString())
                    .holdingId(holding.getId())
                    .type(TransactionType.buy)
                    .date(LocalDate.now())
                    .quantity(shares)
                    .price(price)
                    .fee(BigDecimal.ZERO)
                    .total(total)
                    .source("manual")
                    .build();
            transactionRepository.save(initTx);
            log.info("为持仓 {} 创建初始买入交易: {}份 @ ¥{}", holding.getName(), shares, price);
        } catch (Exception e) {
            log.warn("创建初始交易记录失败: {}", e.getMessage());
        }

        // 根据抓取的分红数据计算预测年分红
        calculatePredictedDividend(holding);

        // 重新计算所有指标（成本息率、市值息率、回本进度等）
        recalculateHoldingMetrics(holding);

        holding = holdingRepository.save(holding);

        return toDTO(holding);
    }

    @Transactional
    public HoldingDTO updateHolding(String id, UpdateHoldingReq req) {
        Holding holding = holdingRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(BusinessException::holdingNotFound);

        boolean sharesOrCostChanged = false;

        if (req.getName() != null) holding.setName(req.getName());
        if (req.getCostAlgorithm() != null) {
            holding.setCostAlgorithm(CostAlgorithm.valueOf(req.getCostAlgorithm()));
        }
        if (req.getShares() != null) {
            holding.setShares(req.getShares());
            sharesOrCostChanged = true;
            // 份额变了，联动更新市值 = 份额 × 最新净值（如果有的话）
            updateMarketValueFromLatestPrice(holding);
        }
        if (req.getCostPerShare() != null) {
            holding.setCostPerShare(req.getCostPerShare());
            sharesOrCostChanged = true;
            // 总成本 = 份额 × 每份成本
            BigDecimal totalCost = holding.getShares()
                    .multiply(req.getCostPerShare())
                    .setScale(2, RoundingMode.HALF_UP);
            holding.setCost(totalCost);
        }
        if (req.getAssetCategory() != null) holding.setAssetCategory(req.getAssetCategory());

        // 如果份额或每份成本被手动改了，跳过交易记录推算的成本，直接用用户输入值
        // 只重算衍生指标（息率、回本年限等）
        recalculateHoldingMetrics(holding, sharesOrCostChanged);

        holding = holdingRepository.save(holding);
        return toDTO(holding);
    }

    /**
     * 用最新净值 × 份额 更新市值；如果没有最新净值，用每份成本兜底
     */
    private void updateMarketValueFromLatestPrice(Holding holding) {
        BigDecimal price = holding.getLatestPrice();
        if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            holding.setMarketValue(holding.getShares()
                    .multiply(price)
                    .setScale(2, RoundingMode.HALF_UP));
        } else if (holding.getCostPerShare() != null && holding.getCostPerShare().compareTo(BigDecimal.ZERO) > 0) {
            holding.setMarketValue(holding.getShares()
                    .multiply(holding.getCostPerShare())
                    .setScale(2, RoundingMode.HALF_UP));
        }
    }

    @Transactional
    public void deleteHolding(String id) {
        Holding holding = holdingRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(BusinessException::holdingNotFound);

        // 物理级联删除：交易 → 分红事件 → 持仓
        int txCount = transactionRepository.deleteByHoldingId(id);
        int evCount = dividendEventRepository.deleteByHoldingId(id);
        holdingRepository.delete(holding);

        log.info("已删除持仓 {} (ID: {})，连带删除 {} 条交易记录和 {} 条分红事件",
                holding.getName(), id, txCount, evCount);
    }

    public void recalculateHoldingMetrics(Holding holding) {
        recalculateHoldingMetrics(holding, false);
    }

    /**
     * 重新计算持仓指标
     * @param skipCostRecalc 是否跳过从交易记录推算成本（编辑持仓手动设置了份额/每份成本时为 true）
     */
    public void recalculateHoldingMetrics(Holding holding, boolean skipCostRecalc) {
        // 获取该持仓的所有交易
        List<Transaction> transactions = transactionRepository.findByHoldingId(holding.getId());

        BigDecimal totalBuy = BigDecimal.ZERO;
        BigDecimal totalSell = BigDecimal.ZERO;
        BigDecimal totalBuyShares = BigDecimal.ZERO;

        for (Transaction tx : transactions) {
            switch (tx.getType()) {
                case buy:
                    totalBuy = totalBuy.add(tx.getTotal());
                    totalBuyShares = totalBuyShares.add(tx.getQuantity());
                    break;
                case sell:
                    totalSell = totalSell.add(tx.getTotal());
                    break;
                case reinvest:
                    totalBuy = totalBuy.add(tx.getTotal());
                    totalBuyShares = totalBuyShares.add(tx.getQuantity());
                    break;
                case bonus_share:
                    // 送股不增加成本
                    totalBuyShares = totalBuyShares.add(tx.getQuantity());
                    break;
            }
        }

        BigDecimal totalDividend = holding.getTotalDividendReceived();
        BigDecimal currentShares = holding.getShares();

        BigDecimal costPerShare;
        BigDecimal netInvestment;
        BigDecimal totalCost;

        if (skipCostRecalc) {
            // 编辑持仓模式：保持用户手动设置的每份成本和总成本，不覆盖
            costPerShare = holding.getCostPerShare() != null ?
                    holding.getCostPerShare() : BigDecimal.ZERO;
            totalCost = holding.getCost() != null ?
                    holding.getCost() : BigDecimal.ZERO;
            // 净投入也从交易记录计算（不受编辑持仓影响）
            netInvestment = costCalculator.calculateNetInvestment(
                    holding.getCostAlgorithm(),
                    totalBuy, totalSell, totalDividend
            );
            if (netInvestment.compareTo(BigDecimal.ZERO) == 0 && totalBuy.compareTo(BigDecimal.ZERO) == 0) {
                netInvestment = holding.getCost();
            }
            // 同步写入 netInvestment，避免后续查询时被覆盖
            holding.setNetInvestment(netInvestment);
        } else {
            // 交易模式：从交易记录重新计算一切
            costPerShare = costCalculator.calculateCostPerShare(
                    holding.getCostAlgorithm(),
                    totalBuy, totalSell, totalDividend,
                    totalBuyShares, currentShares
            );

            netInvestment = costCalculator.calculateNetInvestment(
                    holding.getCostAlgorithm(),
                    totalBuy, totalSell, totalDividend
            );

            totalCost = costCalculator.calculateTotalCost(costPerShare, currentShares);

            // 如果没有交易，初始净投入 = 创建时的成本
            if (netInvestment.compareTo(BigDecimal.ZERO) == 0 && totalBuy.compareTo(BigDecimal.ZERO) == 0) {
                netInvestment = holding.getCost();
                totalCost = holding.getCost();
            }

            holding.setCostPerShare(costPerShare);
            holding.setCost(totalCost);
            holding.setNetInvestment(netInvestment);
        }

        // 计算预测每股分红（基于预测年分红 / 份额）
        BigDecimal predictedDividendPerShare = BigDecimal.ZERO;
        if (currentShares.compareTo(BigDecimal.ZERO) > 0) {
            predictedDividendPerShare = holding.getPredictedDividend()
                    .divide(currentShares, 4, RoundingMode.HALF_UP);
        }

        // 计算成本息率
        BigDecimal dividendRate = costCalculator.calculateDividendRate(
                predictedDividendPerShare, costPerShare
        );

        // 计算股价息率（用最新净值）
        BigDecimal latestPrice = holding.getLatestPrice() != null ?
                holding.getLatestPrice() : BigDecimal.ZERO;
        BigDecimal priceDividendRate = costCalculator.calculatePriceDividendRate(
                predictedDividendPerShare, latestPrice
        );

        // 计算回本进度
        BigDecimal recoveryRate = costCalculator.calculateRecoveryRate(
                totalDividend, netInvestment
        );

        // 计算预计回本年限
        BigDecimal recoveryYears = costCalculator.calculateRecoveryYears(
                netInvestment, totalDividend, holding.getPredictedDividend()
        );

        // 计算复投回本年限（用实体中存储的最新净值）
        BigDecimal reinvestYears = costCalculator.calculateReinvestRecoveryYears(
                netInvestment, totalDividend, holding.getPredictedDividend(),
                currentShares, latestPrice
        );

        // 更新持仓指标
        holding.setDividendRate(dividendRate);
        holding.setPriceDividendRate(priceDividendRate);
        holding.setDividendRecoveryRate(recoveryRate);
        holding.setEstimatedRecoveryYears(recoveryYears);
        holding.setReinvestRecoveryYears(reinvestYears);

        // 一致性自检：手动 costPerShare 与交易推算值的偏差
        if (skipCostRecalc && totalBuyShares.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal txAvgCost = totalBuy.divide(totalBuyShares, 4, RoundingMode.HALF_UP);
            if (costPerShare.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ratio = costPerShare.divide(txAvgCost, 2, RoundingMode.HALF_UP);
                if (ratio.compareTo(new BigDecimal("0.1")) < 0
                        || ratio.compareTo(new BigDecimal("10")) > 0) {
                    log.warn("⚠️ 成本异常 [{}] 手动设置每份成本={} vs 交易加权均价={}，偏差={}倍",
                            holding.getName(), costPerShare, txAvgCost, ratio);
                }
            }
        }
    }

    /**
     * 计算预测年分红：基于基金历史分红记录，通过频率识别计算年均每份分红，再乘以持有份额
     */
    public void calculatePredictedDividend(Holding holding) {
        // 仅对基金/ETF类型计算分红
        if (holding.getType() != HoldingType.fund && holding.getType() != HoldingType.ETF) {
            holding.setPredictedDividend(BigDecimal.ZERO);
            return;
        }

        String fundCode = holding.getCode();
        List<FundDividendRecord> allRecords = fundDividendRecordRepository.findByFundCodeOrderByExDateDesc(fundCode);

        // 只取近 3 年的记录，排除异常旧数据
        LocalDate cutoff = LocalDate.now().minusYears(3);
        List<FundDividendRecord> records = allRecords.stream()
                .filter(r -> r.getExDate() != null && !r.getExDate().isBefore(cutoff))
                .collect(Collectors.toList());

        if (records.isEmpty() && !allRecords.isEmpty()) {
            // 如果近3年没记录但总记录不为空，可能是数据太久远，用全部记录
            records = allRecords;
        }

        if (records.isEmpty()) {
            // 没有分红记录，尝试抓取
            try {
                log.info("{} 无本地分红记录，触发抓取", fundCode);
                fundDividendScrapeService.scrapeAndSave(fundCode);
                records = fundDividendRecordRepository.findByFundCodeOrderByExDateDesc(fundCode);
            } catch (Exception e) {
                log.warn("抓取 {} 分红数据失败: {}", fundCode, e.getMessage());
            }
        }

        if (!records.isEmpty()) {
            DividendInfoDTO info = fundDividendScrapeService.calculateWithFrequency(records, holding.getType().name());
            if (info != null && info.getAnnualDividendPerShare() != null
                    && info.getAnnualDividendPerShare().compareTo(BigDecimal.ZERO) > 0) {
                // 预测总分红 = 每份年分红 × 份额
                BigDecimal totalPredicted = info.getAnnualDividendPerShare()
                        .multiply(holding.getShares())
                        .setScale(2, RoundingMode.HALF_UP);
                holding.setPredictedDividend(totalPredicted);
                log.info("计算 {} 预测年分红: {}元 (每份年分红: {}, 频率: {}, 份额: {})",
                        holding.getName(), totalPredicted, info.getAnnualDividendPerShare(),
                        info.getDividendFrequencyDesc(), holding.getShares());
                return;
            }
        }

        // 无分红数据
        holding.setPredictedDividend(BigDecimal.ZERO);
    }

    /**
     * 计算累计已获分红：汇总该持仓所有已到账分红事件的金额
     */
    private void calculateTotalDividendReceived(Holding holding) {
        List<DividendEvent> distributedEvents = dividendEventRepository
                .findByHoldingIdAndStatus(holding.getId(), EventStatus.distributed);

        BigDecimal total = distributedEvents.stream()
                .map(DividendEvent::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.compareTo(BigDecimal.ZERO) > 0) {
            holding.setTotalDividendReceived(total);
            log.info("计算 {} 累计已获分红: {}元 (来自{}条分红事件)",
                    holding.getName(), total, distributedEvents.size());
        } else {
            // 如果分红事件为空，保持0
            holding.setTotalDividendReceived(BigDecimal.ZERO);
        }
    }

    @Transactional
    public HoldingDTO updateHoldingCategory(String id, UpdateHoldingCategoryReq req) {
        Holding holding = holdingRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(BusinessException::holdingNotFound);
        holding.setAssetCategory(req.getAssetCategory());
        holding = holdingRepository.save(holding);
        log.info("更新持仓 {} 分类为: {}", holding.getName(), req.getAssetCategory());
        return toDTO(holding);
    }

    private HoldingDTO toDTO(Holding holding) {
        // 市值 = 份额 × 最新净值（计算值，不读缓存）
        BigDecimal marketValue;
        if (holding.getLatestPrice() != null && holding.getLatestPrice().compareTo(BigDecimal.ZERO) > 0) {
            marketValue = holding.getShares()
                    .multiply(holding.getLatestPrice())
                    .setScale(2, RoundingMode.HALF_UP);
        } else {
            // 无净值时用成本兜底
            marketValue = holding.getCost() != null ? holding.getCost() : BigDecimal.ZERO;
        }

        return HoldingDTO.builder()
                .id(holding.getId())
                .name(holding.getName())
                .code(holding.getCode())
                .type(holding.getType().name())
                .costAlgorithm(holding.getCostAlgorithm().name())
                .shares(holding.getShares())
                .costPerShare(holding.getCostPerShare())
                .cost(holding.getCost())
                .marketValue(marketValue)
                .latestPrice(holding.getLatestPrice())
                .priceDate(holding.getPriceDate())
                .predictedDividend(holding.getPredictedDividend())
                .dividendRate(holding.getDividendRate())
                .priceDividendRate(holding.getPriceDividendRate())
                .totalDividendReceived(holding.getTotalDividendReceived())
                .netInvestment(holding.getNetInvestment())
                .dividendRecoveryRate(holding.getDividendRecoveryRate())
                .estimatedRecoveryYears(holding.getEstimatedRecoveryYears())
                .reinvestRecoveryYears(holding.getReinvestRecoveryYears())
                .color(holding.getColor())
                .assetCategory(holding.getAssetCategory())
                .build();
    }
}
