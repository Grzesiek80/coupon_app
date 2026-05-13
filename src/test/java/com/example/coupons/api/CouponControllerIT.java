package com.example.coupons.api;

import com.example.coupons.geoip.GeoIpClient;
import com.example.coupons.geoip.GeoIpResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CouponControllerIT {

    @Autowired MockMvc mvc;

    @TestConfiguration
    static class StubGeoIpConfig {
        @Bean
        GeoIpClient geoIpClient() {
            return ip -> GeoIpResult.success("PL");
        }
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

