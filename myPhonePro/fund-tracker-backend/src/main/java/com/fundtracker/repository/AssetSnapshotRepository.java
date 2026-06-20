package com.fundtracker.repository;

import com.fundtracker.model.entity.AssetSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AssetSnapshotRepository extends JpaRepository<AssetSnapshot, String> {
    Optional<AssetSnapshot> findTopByOrderByDateDesc();
    Optional<AssetSnapshot> findByDate(LocalDate date);
    List<AssetSnapshot> findByDateAfterOrderByDateAsc(LocalDate date);
}
