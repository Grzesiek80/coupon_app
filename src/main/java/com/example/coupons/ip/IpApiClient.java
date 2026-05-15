package com.example.coupons.ip;

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
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class IpApiClient implements IpClient {
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final Cache<String, IpResult> geoCache;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public IpApiClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.ip.base-url}") String baseUrl,
            @Value("${app.ip.timeout}") Duration timeout,
            Cache<String, IpResult> geoCache,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry
    ) {
        ClientHttpRequestFactorySettings settings =
                ClientHttpRequestFactorySettings.defaults().withTimeouts(timeout, timeout);
        this.restTemplate = restTemplateBuilder.requestFactorySettings(settings).build();
        this.baseUrl = baseUrl;
        this.geoCache = geoCache;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("geoIp");
        this.retry = retryRegistry.retry("geoIp");
    }

    @Override
    public IpResult resolveCountry(String ip) {
        if (ip == null || ip.isBlank()) {
            return IpResult.failure("Missing IP address");
        }

        String key = ip.trim();
        IpResult cached = geoCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        IpResult resolved = lookup(key);
        if (resolved.success()) {
            geoCache.put(key, resolved);
        }
        return resolved;
    }

    private IpResult lookup(String ip) {
        String url = baseUrl + "/json/" + ip + "?fields=status,countryCode,message";
        Supplier<ResponseEntity<IpApiResponse>> http =
                () -> restTemplate.getForEntity(url, IpApiResponse.class);
        Supplier<ResponseEntity<IpApiResponse>> resilient =
                Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, http));

        try {
            ResponseEntity<IpApiResponse> resp = resilient.get();
            IpApiResponse body = resp.getBody();

            if (body == null) {
                return IpResult.failure("Empty IP response");
            }
            if (!"success".equalsIgnoreCase(body.status())) {
                return IpResult.failure(body.message() != null ? body.message() : "IP lookup failed");
            }
            if (body.countryCode() == null || body.countryCode().isBlank()) {
                return IpResult.failure("Missing countryCode in IP response");
            }
            return IpResult.success(body.countryCode().toUpperCase(Locale.ROOT));
        } catch (CallNotPermittedException e) {
            return IpResult.failure("IP temporarily unavailable");
        } catch (RuntimeException ex) {
            for (Throwable c = ex; c != null; c = c.getCause()) {
                if (c instanceof RestClientException) {
                    return IpResult.failure("IP lookup error");
                }
            }
            throw ex;
        }
    }

    private record IpApiResponse(String status, String countryCode, String message) {}
}
