package com.example.coupons.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.coupons.domain.Coupon;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class CouponRepositoryTest {

    @Autowired
    private CouponRepository couponRepository;

    @Test
    void shouldIncrementUseCountWhenCouponIsNotExhausted() {
        Coupon coupon = new Coupon(UUID.randomUUID(), "TEST", Instant.now(), 2, 0, "PL");
        coupon = couponRepository.saveAndFlush(coupon);

        int updated = couponRepository.tryIncrementUseCount(coupon.getId());

        assertThat(updated).isEqualTo(1);
        Coupon refreshed = couponRepository.findById(coupon.getId()).orElseThrow();
        assertThat(refreshed.getUsesCount()).isEqualTo(1);
    }

    @Test
    void shouldReturnZeroWhenCouponIsAlreadyExhausted() {
        Coupon coupon = new Coupon(UUID.randomUUID(), "ONEOFF", Instant.now(), 1, 1, "PL");
        coupon = couponRepository.saveAndFlush(coupon);

        int updated = couponRepository.tryIncrementUseCount(coupon.getId());

        assertThat(updated).isEqualTo(0);
        Coupon refreshed = couponRepository.findById(coupon.getId()).orElseThrow();
        assertThat(refreshed.getUsesCount()).isEqualTo(1);
    }
}
