package com.fundtracker.service;

import com.fundtracker.exception.BusinessException;
import com.fundtracker.model.dto.*;
import com.fundtracker.model.entity.DividendEvent;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.enums.EventStatus;
import com.fundtracker.model.enums.EventType;
import com.fundtracker.model.enums.TransactionType;
import com.fundtracker.repository.DividendEventRepository;
import com.fundtracker.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final DividendEventRepository eventRepository;
    private final HoldingRepository holdingRepository;
    private final ManualAssetService manualAssetService;
    private final TransactionService transactionService;
    private final FundNavScrapeService fundNavScrapeService;

    public List<DividendEventDTO> listEvents(String holdingId, String month,
                                              String dateFrom, String dateTo,
                                              String type, String status) {
        List<DividendEvent> events;

        if (month != null && !month.isEmpty()) {
            // 按月份筛选: "2024-11"
            String[] parts = month.split("-");
            int year = Integer.parseInt(parts[0]);
            int monthNum = Integer.parseInt(parts[1]);
            LocalDate start = LocalDate.of(year, monthNum, 1);
            LocalDate end = start.plusMonths(1).minusDays(1);

            if (holdingId != null && !holdingId.isEmpty()) {
                events = eventRepository.findByHoldingIdAndDateBetween(holdingId, start, end);
            } else {
                events = eventRepository.findByDateBetweenOrderByDate(start, end);
            }
        } else if (dateFrom != null && dateTo != null) {
            events = eventRepository.findByDateBetweenOrderByDate(
                    LocalDate.parse(dateFrom), LocalDate.parse(dateTo));
        } else if (holdingId != null) {
            events = eventRepository.findByHoldingIdOrderByDateDesc(holdingId);
        } else if (type != null && !type.isEmpty() && status != null && !status.isEmpty()) {
            // 同时有 type 和 status 时利用数据库过滤，避免 findAll()
            events = eventRepository.findByTypeAndStatus(type, status);
        } else {
            events = eventRepository.findAll();
        }

        return events.stream()
                .filter(e -> type == null || e.getType().name().equals(type))
                .filter(e -> status == null || e.getStatus().name().equals(status))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<DividendEventDTO> getEventsByDate(String date) {
        LocalDate localDate = LocalDate.parse(date);
        return eventRepository.findByDateOrderByHoldingName(localDate)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public DividendEventDTO createEvent(CreateEventReq req) {
        Holding holding = holdingRepository.findByIdAndDeletedFalse(req.getHoldingId())
                .orElseThrow(BusinessException::holdingNotFound);

        DividendEvent event = DividendEvent.builder()
                .id(UUID.randomUUID().toString())
                .holdingId(req.getHoldingId())
                .holdingName(holding.getName())
                .type(EventType.valueOf(req.getType()))
                .date(LocalDate.parse(req.getDate()))
                .amount(req.getAmount() != null ? req.getAmount() : BigDecimal.ZERO)
                .status(EventStatus.pending)
                .description(req.getDescription() != null ? req.getDescription() : "")
                .build();

        event = eventRepository.save(event);
        return toDTO(event);
    }

    @Transactional
    public DividendEventDTO markDistributed(String id) {
        DividendEvent event = eventRepository.findById(id)
                .orElseThrow(BusinessException::eventNotFound);

        event.setStatus(EventStatus.distributed);
        DividendEvent savedEvent = eventRepository.save(event);

        // 更新持仓的累计已收分红
        if (savedEvent.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            final BigDecimal distributeAmount = savedEvent.getAmount();
            holdingRepository.findByIdAndDeletedFalse(savedEvent.getHoldingId())
                    .ifPresent(holding -> {
                        holding.setTotalDividendReceived(
                                holding.getTotalDividendReceived().add(distributeAmount));
                        holdingRepository.save(holding);

                        if (Boolean.TRUE.equals(holding.getDividendReinvest())) {
                            // 复投模式：按最新净值折算份额，创建 reinvest 交易
                            try {
                                BigDecimal nav = holding.getLatestPrice();
                                if (nav == null || nav.compareTo(BigDecimal.ZERO) <= 0) {
                                    FundNavScrapeService.LatestNavResult navResult = fundNavScrapeService.incrementalUpdate(holding.getCode());
                                    if (navResult != null) {
                                        nav = navResult.unitNav();
                                    }
                                }

                                BigDecimal quantity;
                                BigDecimal price;
                                if (nav != null && nav.compareTo(BigDecimal.ZERO) > 0) {
                                    price = nav;
                                    quantity = distributeAmount.divide(price, 4, RoundingMode.HALF_UP);
                                } else {
                                    // 无净值时用 1 元兜底（数量=金额）
                                    price = BigDecimal.ONE;
                                    quantity = distributeAmount;
                                }

                                CreateTransactionReq reinvestReq = new CreateTransactionReq();
                                reinvestReq.setHoldingId(holding.getId());
                                reinvestReq.setType(TransactionType.reinvest.name());
                                reinvestReq.setDate(LocalDate.now().toString());
                                reinvestReq.setQuantity(quantity);
                                reinvestReq.setPrice(price);
                                reinvestReq.setFee(BigDecimal.ZERO);
                                reinvestReq.setSource("dividend_reinvest");
                                transactionService.createTransaction(reinvestReq);
                                log.info("分红复投: {} 金额 {}, NAV={}, 买入 {} 份",
                                        holding.getName(), distributeAmount, price, quantity);
                            } catch (Exception e) {
                                log.error("分红复投失败: {}", e.getMessage());
                            }
                        } else {
                            // 现金模式：分红到账，增加现金
                            manualAssetService.adjustCash(holding.getId(), distributeAmount);
                            log.info("分红到账: {} 现金 +{}", holding.getName(), distributeAmount);
                        }
                    });
        }

        return toDTO(savedEvent);
    }

    @Transactional
    public CancelEventResp cancelEvent(String id) {
        DividendEvent event = eventRepository.findById(id)
                .orElseThrow(BusinessException::eventNotFound);

        if (event.getStatus() == EventStatus.distributed) {
            throw new BusinessException(2002, "已到账的事件不可取消");
        }

        event.setStatus(EventStatus.cancelled);
        eventRepository.save(event);

        return CancelEventResp.builder()
                .id(event.getId())
                .status("cancelled")
                .updatedAt(LocalDateTime.now().toString())
                .build();
    }

    @Transactional
    public DividendEventDTO convertToReinvest(String id) {
        DividendEvent event = eventRepository.findById(id)
                .orElseThrow(BusinessException::eventNotFound);

        if (event.getStatus() != EventStatus.distributed) {
            throw new BusinessException(2003, "只有已到账的分红才能转为复投");
        }
        if (event.getAmount() == null || event.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(2004, "金额为0的分红无需复投");
        }

        final BigDecimal amount = event.getAmount();
        holdingRepository.findByIdAndDeletedFalse(event.getHoldingId())
                .ifPresent(holding -> {
                    // 扣除之前加到现金里的分红金额
                    manualAssetService.adjustCash(holding.getId(), amount.negate());
                    log.info("分红转复投: {} 现金 -{}", holding.getName(), amount);

                    // 按最新净值买入份额
                    try {
                        BigDecimal nav = holding.getLatestPrice();
                        if (nav == null || nav.compareTo(BigDecimal.ZERO) <= 0) {
                            FundNavScrapeService.LatestNavResult navResult = fundNavScrapeService.incrementalUpdate(holding.getCode());
                            if (navResult != null) {
                                nav = navResult.unitNav();
                            }
                        }

                        BigDecimal quantity;
                        BigDecimal price;
                        if (nav != null && nav.compareTo(BigDecimal.ZERO) > 0) {
                            price = nav;
                            quantity = amount.divide(price, 4, RoundingMode.HALF_UP);
                        } else {
                            price = BigDecimal.ONE;
                            quantity = amount;
                        }

                        CreateTransactionReq reinvestReq = new CreateTransactionReq();
                        reinvestReq.setHoldingId(holding.getId());
                        reinvestReq.setType(TransactionType.reinvest.name());
                        reinvestReq.setDate(LocalDate.now().toString());
                        reinvestReq.setQuantity(quantity);
                        reinvestReq.setPrice(price);
                        reinvestReq.setFee(BigDecimal.ZERO);
                        reinvestReq.setSource("dividend_reinvest");
                        transactionService.createTransaction(reinvestReq);
                        log.info("分红转复投: {} 金额 {}, NAV={}, 买入 {} 份",
                                holding.getName(), amount, price, quantity);
                    } catch (Exception e) {
                        log.error("分红转复投买入失败: {}", e.getMessage());
                        // 买入失败时回滚现金扣除
                        manualAssetService.adjustCash(holding.getId(), amount);
                        throw new RuntimeException("复投买入失败", e);
                    }
                });

        return toDTO(event);
    }

    private DividendEventDTO toDTO(DividendEvent event) {
        return DividendEventDTO.builder()
                .id(event.getId())
                .holdingId(event.getHoldingId())
                .holdingName(event.getHoldingName())
                .type(event.getType().name())
                .date(event.getDate().toString())
                .amount(event.getAmount())
                .status(event.getStatus().name())
                .description(event.getDescription())
                .participated(event.getParticipated())
                .build();
    }
}
