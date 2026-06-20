package com.fundtracker.service;

import com.fundtracker.model.dto.CoverageCategoryDTO;
import com.fundtracker.model.dto.UpdateCoverageCategoryReq;
import com.fundtracker.model.entity.CoverageCategory;
import com.fundtracker.repository.CoverageCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CoverageCategoryService {

    private final CoverageCategoryRepository repository;

    public List<CoverageCategoryDTO> listAll() {
        List<CoverageCategory> categories = repository.findAll();
        if (categories.isEmpty()) {
            // 初始化默认数据
            return initDefaults();
        }
        return categories.stream().map(this::toDTO).collect(Collectors.toList());
    }

    private List<CoverageCategoryDTO> initDefaults() {
        CoverageCategory[] defaults = {
                createCat("话费", "phone_android", 80, "#FF7A45"),
                createCat("养车", "directions_car", 40, "#4CAF50"),
                createCat("娱乐", "confirmation_number", 30, "#9C27B0"),
                createCat("医药", "medical_services", 25, "#2196F3"),
                createCat("午餐", "restaurant", 60, "#FF9800")
        };
        for (CoverageCategory cat : defaults) {
            repository.save(cat);
        }
        return repository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    private CoverageCategory createCat(String name, String icon, int percentage, String color) {
        return CoverageCategory.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .icon(icon)
                .percentage(java.math.BigDecimal.valueOf(percentage))
                .color(color)
                .build();
    }

    @Transactional
    public CoverageCategoryDTO update(String id, UpdateCoverageCategoryReq req) {
        CoverageCategory cat = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("类目不存在"));
        if (req.getName() != null) cat.setName(req.getName());
        if (req.getIcon() != null) cat.setIcon(req.getIcon());
        if (req.getPercentage() != null) cat.setPercentage(req.getPercentage());
        if (req.getColor() != null) cat.setColor(req.getColor());
        cat = repository.save(cat);
        return toDTO(cat);
    }

    private CoverageCategoryDTO toDTO(CoverageCategory cat) {
        return CoverageCategoryDTO.builder()
                .id(cat.getId())
                .name(cat.getName())
                .icon(cat.getIcon())
                .percentage(cat.getPercentage())
                .color(cat.getColor())
                .build();
    }
}
