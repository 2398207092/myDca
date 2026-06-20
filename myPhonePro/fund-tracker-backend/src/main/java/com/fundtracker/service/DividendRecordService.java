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
        String statusFilter = status != null ? status : EventStatus.distributed.name();

        List<DividendEvent> allEvents = eventRepository.findAll();

        return allEvents.stream()
                .filter(e -> statusFilter.equals(e.getStatus().name()))
                .filter(e -> holdingId == null || holdingId.equals(e.getHoldingId()))
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
