package com.fundtracker.repository;

import com.fundtracker.model.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {

    List<Account> findByUserIdAndDeletedFalseOrderBySortOrderAsc(String userId);

    Optional<Account> findByIdAndUserIdAndDeletedFalse(String id, String userId);

    boolean existsByUserIdAndNameAndDeletedFalse(String userId, String name);

    /** 显式 JPQL：boolean 属性 isDefault 的派生查询名有歧义，避免踩坑 */
    @Query("select a from Account a where a.userId = :userId and a.deleted = false and a.isDefault = true")
    Optional<Account> findDefault(@Param("userId") String userId);

    @Query("select count(a) from Account a where a.userId = :userId and a.deleted = false")
    long countActive(@Param("userId") String userId);
}
