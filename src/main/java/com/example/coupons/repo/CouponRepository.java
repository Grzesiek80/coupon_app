package com.example.coupons.repo;

import com.example.coupons.domain.Coupon;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {
    Optional<Coupon> findByCodeNormalized(String codeNormalized);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Coupon c set c.usesCount = c.usesCount + 1 where c.id = :id and c.usesCount < c.maxUses")
    int tryIncrementUseCount(@Param("id") UUID id);
}

