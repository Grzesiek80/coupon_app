package com.example.coupons.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.coupons.geoip.GeoIpClient;
import com.example.coupons.geoip.GeoIpResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CouponControllerIT {

    private static final String GEOIP_UNAVAILABLE_MESSAGE =
            "This request could not be completed right now. Please try again in a moment.";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    GeoIpClient geoIpClient;

    @BeforeEach
    void geoIpPolandByDefault() {
        when(geoIpClient.resolveCountryIso2(anyString())).thenReturn(GeoIpResult.success("PL"));
    }

    @Test
    void create_coupon_returns_created_with_body() throws Exception {
        mvc.perform(post("/api/v1/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"SPRING24","maxUses":5,"countryIso2":"pl"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SPRING24"))
                .andExpect(jsonPath("$.maxUses").value(5))
                .andExpect(jsonPath("$.usesCount").value(0))
                .andExpect(jsonPath("$.countryIso2").value("PL"));
    }

    @Test
    void create_coupon_duplicate_code_returns_conflict() throws Exception {
        String body = """
                {"code":"DUPLICATE","maxUses":1,"countryIso2":"PL"}
                """;
        mvc.perform(post("/api/v1/coupons").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/coupons").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Coupon code already exists"));
    }

    @Test
    void create_coupon_invalid_country_iso_returns_bad_request() throws Exception {
        mvc.perform(post("/api/v1/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"X","maxUses":1,"countryIso2":"POL"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void use_coupon_not_found_returns_404() throws Exception {
        mvc.perform(post("/api/v1/coupons/use")
                        .header("X-Forwarded-For", "203.0.113.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"missing","userId":"u1"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.reason").value("COUPON_NOT_FOUND"));
    }

    @Test
    void use_coupon_wrong_country_returns_403() throws Exception {
        mvc.perform(post("/api/v1/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ONLY_PL","maxUses":10,"countryIso2":"PL"}
                                """))
                .andExpect(status().isCreated());

        when(geoIpClient.resolveCountryIso2(anyString())).thenReturn(GeoIpResult.success("DE"));

        mvc.perform(post("/api/v1/coupons/use")
                        .header("X-Forwarded-For", "198.51.100.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"only_pl","userId":"u2"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.reason").value("COUNTRY_NOT_ALLOWED"));
    }

    @Test
    void use_coupon_geoip_unavailable_returns_503_with_retry_after_and_message() throws Exception {
        mvc.perform(post("/api/v1/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"GEO503","maxUses":3,"countryIso2":"PL"}
                                """))
                .andExpect(status().isCreated());

        when(geoIpClient.resolveCountryIso2(anyString())).thenReturn(GeoIpResult.failure("unreachable"));

        mvc.perform(post("/api/v1/coupons/use")
                        .header("X-Forwarded-For", "203.0.113.10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"geo503","userId":"u1"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.reason").value("GEOIP_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(GEOIP_UNAVAILABLE_MESSAGE));
    }

    @Test
    void use_coupon_exhausted_returns_409() throws Exception {
        mvc.perform(post("/api/v1/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ONESHOT","maxUses":1,"countryIso2":"PL"}
                                """))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/coupons/use")
                        .header("X-Forwarded-For", "192.0.2.5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"oneshot"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true));

        mvc.perform(post("/api/v1/coupons/use")
                        .header("X-Forwarded-For", "192.0.2.6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"oneshot"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.reason").value("COUPON_EXHAUSTED"));
    }

    @Test
    void use_coupon_blank_code_returns_bad_request() throws Exception {
        mvc.perform(post("/api/v1/coupons/use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"","userId":"u1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void create_and_use_coupon_success() throws Exception {
        mvc.perform(post("/api/v1/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"WIOSNA","maxUses":2,"countryIso2":"PL"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maxUses").value(2));

        mvc.perform(post("/api/v1/coupons/use")
                        .header("X-Forwarded-For", "1.2.3.4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"wiosna","userId":"u1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true));
    }

    @Test
    void same_user_cannot_use_twice() throws Exception {
        mvc.perform(post("/api/v1/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PROMO","maxUses":10,"countryIso2":"PL"}
                                """))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/coupons/use")
                        .header("X-Forwarded-For", "1.2.3.4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"promo","userId":"u1"}
                                """))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/coupons/use")
                        .header("X-Forwarded-For", "1.2.3.4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"promo","userId":"u1"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.reason").value("ALREADY_USED_BY_USER"));
    }
}
