package com.example.coupons.geoip;

public interface GeoIpClient {
    GeoIpResult resolveCountryIso2(String ip);
}

