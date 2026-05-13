package com.example.coupons.api.dto;

import java.time.Instant;
import java.util.UUID;

public record CreateCouponResponse(
        UUID id,
        String code,
        Instant createdAt,
        int maxUses,
        int usesCount,
        String countryIso2
) {}

