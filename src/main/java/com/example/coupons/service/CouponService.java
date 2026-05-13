package com.example.coupons.service;

import com.example.coupons.domain.Coupon;
import com.example.coupons.domain.CouponCodeNormalizer;
import com.example.coupons.domain.CouponUsage;
import com.example.coupons.geoip.GeoIpClient;
import com.example.coupons.geoip.GeoIpResult;
import com.example.coupons.repo.CouponRepository;
import com.example.coupons.repo.CouponUsageRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class CouponService {
    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final GeoIpClient geoIpClient;

    public CouponService(CouponRepository couponRepository, CouponUsageRepository couponUsageRepository, GeoIpClient geoIpClient) {
        this.couponRepository = couponRepository;
        this.couponUsageRepository = couponUsageRepository;
        this.geoIpClient = geoIpClient;
    }

    @Transactional
    public Coupon createCoupon(String code, int maxUses, String countryIso2) {
        String normalized = CouponCodeNormalizer.normalize(code);
        String country = countryIso2.trim().toUpperCase(Locale.ROOT);

        Coupon coupon = new Coupon(
                UUID.randomUUID(),
                normalized,
                Instant.now(),
                maxUses,
                0,
                country
        );

        try {
            return couponRepository.saveAndFlush(coupon);
        } catch (DataIntegrityViolationException ex) {
            throw new CouponValidationException("Coupon code already exists");
        }
    }

    @Transactional
    public UseCouponResult useCoupon(String code, String userId, String ip) {
        String normalized = CouponCodeNormalizer.normalize(code);
        if (normalized == null || normalized.isBlank()) {
            return UseCouponResult.rejected(normalized, "INVALID_REQUEST");
        }

        var couponOpt = couponRepository.findByCodeNormalized(normalized);
        if (couponOpt.isEmpty()) {
            return UseCouponResult.rejected(normalized, "COUPON_NOT_FOUND");
        }
        Coupon coupon = couponOpt.get();

        GeoIpResult geo = geoIpClient.resolveCountryIso2(ip);
        if (!geo.success()) {
            return UseCouponResult.rejected(coupon.getId(), normalized, "GEOIP_UNAVAILABLE");
        }
        if (!coupon.getCountryIso2().equalsIgnoreCase(geo.countryIso2())) {
            return UseCouponResult.rejected(coupon.getId(), normalized, "COUNTRY_NOT_ALLOWED");
        }

        if (userId != null && !userId.isBlank()) {
            String trimmed = userId.trim();
            if (couponUsageRepository.existsByCouponIdAndUserId(coupon.getId(), trimmed)) {
                return UseCouponResult.rejected(coupon.getId(), normalized, "ALREADY_USED_BY_USER");
            }
        }

        int updated = couponRepository.tryIncrementUseCount(coupon.getId());
        if (updated == 0) {
            return UseCouponResult.rejected(coupon.getId(), normalized, "COUPON_EXHAUSTED");
        }

        couponUsageRepository.save(new CouponUsage(
                UUID.randomUUID(),
                coupon.getId(),
                (userId == null || userId.isBlank()) ? null : userId.trim(),
                Instant.now(),
                ip,
                geo.countryIso2()
        ));

        return UseCouponResult.accepted(coupon.getId(), normalized);
    }
}

