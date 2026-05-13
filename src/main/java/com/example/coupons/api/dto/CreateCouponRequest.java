package com.example.coupons.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateCouponRequest(
        @NotBlank
        String code,

        @NotNull
        @Min(1)
        @Max(1_000_000)
        Integer maxUses,

        @NotBlank
        @Pattern(regexp = "^[A-Za-z]{2}$")
        String countryIso2
) {}

