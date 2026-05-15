package com.example.coupons.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "coupon_usage",
        indexes = {
                @Index(name = "idx_coupon_usage_coupon_id", columnList = "coupon_id"),
                @Index(name = "idx_coupon_usage_user_coupon", columnList = "user_id,coupon_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponUsage {
    @Id
    private UUID id;

    @Column(name = "coupon_id", nullable = false)
    private UUID couponId;

    @Column(name = "user_id", length = 128)
    private String userId;

    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    @Column(name = "ip", nullable = false, length = 45)
    private String ip;

    @Column(name = "country_iso2", length = 2)
    private String countryIso2;

    public CouponUsage(UUID id, UUID couponId, String userId, Instant usedAt, String ip, String countryIso2) {
        this.id = id;
        this.couponId = couponId;
        this.userId = userId;
        this.usedAt = usedAt;
        this.ip = ip;
        this.countryIso2 = countryIso2;
    }
}

