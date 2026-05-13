package com.example.coupons.api.dto;

import java.util.UUID;

public record UseCouponResponse(
        UUID couponId,
        String code,
        boolean accepted,
        String reason,
        String message
) {}
