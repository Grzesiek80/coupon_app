package com.example.coupons.ip;

public record IpResult(
        boolean success,
        String country,
        String message
) {
    public static IpResult success(String country) {
        return new IpResult(true, country, null);
    }

    public static IpResult failure(String message) {
        return new IpResult(false, null, message);
    }
}
