package com.fundtracker.service;

import com.fundtracker.model.dto.DividendEventDTO;
import com.fundtracker.model.entity.DividendEvent;
import com.fundtracker.model.enums.EventStatus;
import com.fundtracker.repository.DividendEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 分红记录 - 复用的 DividendEvent 中已到账的数据
 */
@Service
@RequiredArgsConstructor
public class DividendRecordService {

    private final DividendEventRepository eventRepository;

    public List<DividendEventDTO> getRecords(String holdingId, Integer year, String status) {
        EventStatus statusEnum = status != null ? EventStatus.valueOf(status) : EventStatus.distributed;

        // 根据参数选择最优查询方式，避免 findAll() 全表扫描
        List<DividendEvent> events;
        if (holdingId != null) {
            events = eventRepository.findByHoldingIdAndStatus(holdingId, statusEnum);
        } else {
            events = eventRepository.findByStatus(statusEnum);
        }

        // year 过滤无法通过 JPA 方法名推导，在内存中过滤已缩小的数据集
        return events.stream()
                .filter(e -> year == null || e.getDate().getYear() == year)
                .map(this::toDTO)
                .collect(Collectors.toList());
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
                .build();
    }
}
