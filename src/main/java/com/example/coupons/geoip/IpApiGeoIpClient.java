package com.example.coupons.geoip;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class IpApiGeoIpClient implements GeoIpClient {
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final Cache<String, GeoIpResult> geoCache;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public IpApiGeoIpClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.geoip.base-url}") String baseUrl,
            @Value("${app.geoip.timeout}") Duration timeout,
            Cache<String, GeoIpResult> geoCache,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry
    ) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(timeout)
                .setReadTimeout(timeout)
                .build();
        this.baseUrl = baseUrl;
        this.geoCache = geoCache;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("geoIp");
        this.retry = retryRegistry.retry("geoIp");
    }

    @Override
    public GeoIpResult resolveCountryIso2(String ip) {
        if (ip == null || ip.isBlank()) {
            return GeoIpResult.failure("Missing IP address");
        }

        String key = ip.trim();
        GeoIpResult cached = geoCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        GeoIpResult resolved = lookup(key);
        if (resolved.success()) {
            geoCache.put(key, resolved);
        }
        return resolved;
    }

    private GeoIpResult lookup(String ip) {
        String url = baseUrl + "/json/" + ip + "?fields=status,countryCode,message";
        Supplier<ResponseEntity<IpApiResponse>> http =
                () -> restTemplate.getForEntity(url, IpApiResponse.class);
        Supplier<ResponseEntity<IpApiResponse>> resilient =
                Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, http));

        try {
            ResponseEntity<IpApiResponse> resp = resilient.get();
            IpApiResponse body = resp.getBody();

            if (body == null) {
                return GeoIpResult.failure("Empty GeoIP response");
            }
            if (!"success".equalsIgnoreCase(body.status())) {
                return GeoIpResult.failure(body.message() != null ? body.message() : "GeoIP lookup failed");
            }
            if (body.countryCode() == null || body.countryCode().isBlank()) {
                return GeoIpResult.failure("Missing countryCode in GeoIP response");
            }
            return GeoIpResult.success(body.countryCode().toUpperCase(Locale.ROOT));
        } catch (CallNotPermittedException e) {
            return GeoIpResult.failure("GeoIP temporarily unavailable");
        } catch (RuntimeException ex) {
            for (Throwable c = ex; c != null; c = c.getCause()) {
                if (c instanceof RestClientException) {
                    return GeoIpResult.failure("GeoIP lookup error");
                }
            }
            throw ex;
        }
    }

    private record IpApiResponse(String status, String countryCode, String message) {}
}
