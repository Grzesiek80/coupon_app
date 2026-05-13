package com.example.coupons.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UseCouponRequest(
        @NotBlank String code,
        String userId
) {}

