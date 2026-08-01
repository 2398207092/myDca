package com.fundtracker.service;

import com.fundtracker.model.entity.HoldingSnapshot;
import com.fundtracker.repository.HoldingSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 快照记录列表服务（分页查询）。
 * 职责：查询快照记录列表，按日期降序分页返回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotListService {

    private final HoldingSnapshotRepository holdingSnapshotRepository;

    /**
     * 查询快照记录列表（分页），按日期降序。
     */
    public Map<String, Object> listSnapshots(int page, int size) {
        int p = Math.max(page, 0);
        int s = Math.max(size, 1);
        PageRequest pageable = PageRequest.of(p, s,
                Sort.by(Sort.Direction.DESC, "snapshotDate"));
        org.springframework.data.domain.Page<HoldingSnapshot> pageResult = holdingSnapshotRepository.findAll(pageable);
        Map<String, Object> result = new HashMap<>();
        result.put("items", pageResult.getContent());
        result.put("total", pageResult.getTotalElements());
        result.put("page", p);
        result.put("pageSize", s);
        result.put("totalPages", pageResult.getTotalPages());
        return result;
    }
}