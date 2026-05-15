package com.example.coupons.config;

import com.example.coupons.ip.IpResult;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, IpResult> ipCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(24))
                .maximumSize(10_000)
                .build();
    }
}
