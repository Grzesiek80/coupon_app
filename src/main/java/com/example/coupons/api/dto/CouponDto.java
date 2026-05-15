package com.example.coupons.api.dto;

import java.time.Instant;
import java.util.UUID;

public record CouponDto(
        UUID id,
        String code,
        Instant createdAt,
        int maxUses,
        int usesCount,
        String countryIso2
) {
    public static CouponDto from(com.example.coupons.domain.Coupon coupon) {
        return new CouponDto(
                coupon.getId(),
                coupon.getCodeNormalized(),
                coupon.getCreatedAt(),
                coupon.getMaxUses(),
                coupon.getUsesCount(),
                coupon.getCountryIso2()
        );
    }
}