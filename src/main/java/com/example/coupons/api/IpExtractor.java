package com.example.coupons.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

public final class IpExtractor {
    private IpExtractor() {}

    public static String extractClientIp(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                .map(IpExtractor::firstIpFromXForwardedFor)
                .filter(ip -> !ip.isBlank())
                .orElseGet(() -> request.getRemoteAddr());
    }

    private static String firstIpFromXForwardedFor(String headerValue) {
        if (headerValue == null) return null;
        int commaIdx = headerValue.indexOf(',');
        String first = commaIdx >= 0 ? headerValue.substring(0, commaIdx) : headerValue;
        return first.trim();
    }
}

