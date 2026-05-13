package com.example.coupons.geoip;

public record GeoIpResult(
        boolean success,
        String countryIso2,
        String message
) {
    public static GeoIpResult success(String countryIso2) {
        return new GeoIpResult(true, countryIso2, null);
    }

    public static GeoIpResult failure(String message) {
        return new GeoIpResult(false, null, message);
    }
}
