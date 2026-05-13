package com.example.coupons.repo;

import com.example.coupons.domain.CouponUsage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, UUID> {
    boolean existsByCouponIdAndUserId(UUID couponId, String userId);
}

