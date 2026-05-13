package com.example.coupons.geoip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

class IpApiGeoIpClientTest {
    private RestTemplate restTemplate;
    private RestTemplateBuilder restTemplateBuilder;
    private Cache<String, GeoIpResult> geoCache;
    private IpApiGeoIpClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        restTemplateBuilder = mock(RestTemplateBuilder.class);
        when(restTemplateBuilder.requestFactorySettings(any(ClientHttpRequestFactorySettings.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);

        geoCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(24))
                .maximumSize(10_000)
                .build();

        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(2)
                .minimumNumberOfCalls(1)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(1))
                .permittedNumberOfCallsInHalfOpenState(1)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);

        RetryConfig retryConfig = RetryConfig.custom().maxAttempts(1).build();
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);

        client = new IpApiGeoIpClient(
                restTemplateBuilder,
                "http://ip-api.com",
                Duration.ofSeconds(2),
                geoCache,
                circuitBreakerRegistry,
                retryRegistry
        );
    }

    @Test
    void shouldReturnFailureForBlankIpWithoutHttpCall() {
        GeoIpResult result = client.resolveCountryIso2("  ");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("Missing IP");
        verifyNoInteractions(restTemplate);
    }

    @Test
    void shouldUseCacheWhenValueExists() {
        geoCache.put("203.0.113.10", GeoIpResult.success("PL"));

        GeoIpResult result = client.resolveCountryIso2("203.0.113.10");

        assertThat(result.success()).isTrue();
        assertThat(result.countryIso2()).isEqualTo("PL");
        verifyNoInteractions(restTemplate);
    }

    @Test
    void shouldOpenCircuitBreakerAfterRepeatedFailures() {
        doThrow(new RestClientException("failed"))
                .when(restTemplate)
                .getForEntity(anyString(), any(Class.class));

        GeoIpResult firstFailure = client.resolveCountryIso2("203.0.113.10");
        GeoIpResult secondFailure = client.resolveCountryIso2("203.0.113.10");

        assertThat(firstFailure.success()).isFalse();
        assertThat(firstFailure.message()).contains("GeoIP lookup error");
        assertThat(secondFailure.success()).isFalse();
        assertThat(secondFailure.message()).contains("temporarily unavailable");
    }
}
