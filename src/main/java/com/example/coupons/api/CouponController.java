package com.example.coupons.api;

import com.example.coupons.api.dto.CouponDto;
import com.example.coupons.api.dto.CreateCouponRequest;
import com.example.coupons.api.dto.CreateCouponResponse;
import com.example.coupons.api.dto.UseCouponRequest;
import com.example.coupons.api.dto.UseCouponResponse;
import com.example.coupons.service.CouponService;
import com.example.coupons.service.UseCouponResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {
    private static final String GEOIP_UNAVAILABLE_FALLBACK_MESSAGE =
            "This request could not be completed right now. Please try again in a moment.";

    private final CouponService couponService;

    @PostMapping
    public ResponseEntity<CreateCouponResponse> create(@Valid @RequestBody CreateCouponRequest request) {
        CouponDto created = couponService.createCoupon(request.code(), request.maxUses(), request.countryIso2());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateCouponResponse(
                created.id(),
                request.code(),
                created.createdAt(),
                created.maxUses(),
                created.usesCount(),
                created.countryIso2()
        ));
    }

    @PostMapping("/use")
    public ResponseEntity<UseCouponResponse> use(@Valid @RequestBody UseCouponRequest request, HttpServletRequest http) {
        String ip = IpExtractor.extractClientIp(http);
        UseCouponResult result = couponService.useCoupon(request.code(), request.userId(), ip);

        if (result.accepted()) {
            return ResponseEntity.ok(new UseCouponResponse(result.couponId(), request.code(), true, null, null));
        }

        HttpStatus status = switch (result.reason()) {
            case "COUPON_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "COUNTRY_NOT_ALLOWED" -> HttpStatus.FORBIDDEN;
            case "ALREADY_USED_BY_USER" -> HttpStatus.CONFLICT;
            case "COUPON_EXHAUSTED" -> HttpStatus.CONFLICT;
            case "GEOIP_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };

        String message =
                "GEOIP_UNAVAILABLE".equals(result.reason()) ? GEOIP_UNAVAILABLE_FALLBACK_MESSAGE : null;
        ResponseEntity.BodyBuilder reply = ResponseEntity.status(status);
        if ("GEOIP_UNAVAILABLE".equals(result.reason())) {
            reply = reply.header(HttpHeaders.RETRY_AFTER, "60");
        }

        return reply.body(new UseCouponResponse(
                result.couponId(),
                request.code(),
                false,
                result.reason(),
                message));
    }
}

