package com.fundtracker.service;

import com.fundtracker.model.dto.HoldingSearchResult;
import com.fundtracker.service.provider.FundSearchProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 基金搜索服务（门面）
 * 外部数据源逻辑已下沉到 FundSearchProvider（#1 外部数据源层），
 * 本类仅保留业务门面，对外接口不变。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundSearchService {

    private final FundSearchProvider fundSearchProvider;

    public List<HoldingSearchResult> search(String keyword) {
        return fundSearchProvider.search(keyword);
    }
}
