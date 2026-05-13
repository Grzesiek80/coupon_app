package com.example.coupons.service;

import java.util.UUID;

public record UseCouponResult(
        UUID couponId,
        String normalizedCode,
        boolean accepted,
        String reason
) {
    public static UseCouponResult accepted(UUID couponId, String normalizedCode) {
        return new UseCouponResult(couponId, normalizedCode, true, null);
    }

    public static UseCouponResult rejected(UUID couponId, String normalizedCode, String reason) {
        return new UseCouponResult(couponId, normalizedCode, false, reason);
    }

    public static UseCouponResult rejected(String normalizedCode, String reason) {
        return new UseCouponResult(null, normalizedCode, false, reason);
    }
}

