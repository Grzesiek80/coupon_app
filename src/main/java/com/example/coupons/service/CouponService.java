package com.example.coupons.service;

import com.example.coupons.api.dto.CouponDto;
import com.example.coupons.domain.Coupon;
import com.example.coupons.domain.CouponCodeNormalizer;
import com.example.coupons.domain.CouponUsage;
import com.example.coupons.ip.IpClient;
import com.example.coupons.ip.IpResult;
import com.example.coupons.repo.CouponRepository;
import com.example.coupons.repo.CouponUsageRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CouponService {
    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final IpClient ipClient;
    private final TransactionTemplate transactionTemplate;

    public CouponService(
            CouponRepository couponRepository,
            CouponUsageRepository couponUsageRepository,
            IpClient ipClient,
            PlatformTransactionManager transactionManager
    ) {
        this.couponRepository = couponRepository;
        this.couponUsageRepository = couponUsageRepository;
        this.ipClient = ipClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public CouponDto createCoupon(String code, int maxUses, String countryIso2) {
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
            Coupon saved = couponRepository.saveAndFlush(coupon);
            return CouponDto.from(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new CouponValidationException("Coupon code already exists");
        }
    }

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

        IpResult geo = ipClient.resolveCountry(ip);
        if (!geo.success()) {
            return UseCouponResult.rejected(coupon.getId(), normalized, "GEOIP_UNAVAILABLE");
        }
        if (!coupon.getCountryIso2().equalsIgnoreCase(geo.country())) {
            return UseCouponResult.rejected(coupon.getId(), normalized, "COUNTRY_NOT_ALLOWED");
        }

        if (userId != null && !userId.isBlank()) {
            String trimmed = userId.trim();
            if (couponUsageRepository.existsByCouponIdAndUserId(coupon.getId(), trimmed)) {
                return UseCouponResult.rejected(coupon.getId(), normalized, "ALREADY_USED_BY_USER");
            }
        }

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return transactionTemplate.execute(status -> {
                    Coupon current = couponRepository.findById(coupon.getId()).orElseThrow();
                    if (current.getUsesCount() >= current.getMaxUses()) {
                        return UseCouponResult.rejected(current.getId(), normalized, "COUPON_EXHAUSTED");
                    }

                    current.setUsesCount(current.getUsesCount() + 1);
                    couponRepository.saveAndFlush(current);
                    couponUsageRepository.save(new CouponUsage(
                            UUID.randomUUID(),
                            current.getId(),
                            (userId == null || userId.isBlank()) ? null : userId.trim(),
                            Instant.now(),
                            ip,
                            geo.country()
                    ));

                    return UseCouponResult.accepted(current.getId(), normalized);
                });
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == 3) {
                    throw e;
                }
            }
        }

        return UseCouponResult.rejected(coupon.getId(), normalized, "COUPON_EXHAUSTED");
    }
}

