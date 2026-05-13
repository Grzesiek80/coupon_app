package com.example.coupons.domain;

import java.util.Locale;

public final class CouponCodeNormalizer {
    private CouponCodeNormalizer() {}

    public static String normalize(String rawCode) {
        if (rawCode == null) return null;
        return rawCode.trim().toUpperCase(Locale.ROOT);
    }
}

