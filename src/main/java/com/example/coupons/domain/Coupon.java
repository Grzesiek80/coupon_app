package com.example.coupons.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coupon")
public class Coupon {
    @Id
    private UUID id;

    @Column(name = "code_normalized", nullable = false, unique = true, length = 64)
    private String codeNormalized;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "max_uses", nullable = false)
    private int maxUses;

    @Column(name = "uses_count", nullable = false)
    private int usesCount;

    @Column(name = "country_iso2", nullable = false, length = 2)
    private String countryIso2;

    @Version
    private long version;

    protected Coupon() {}

    public Coupon(UUID id, String codeNormalized, Instant createdAt, int maxUses, int usesCount, String countryIso2) {
        this.id = id;
        this.codeNormalized = codeNormalized;
        this.createdAt = createdAt;
        this.maxUses = maxUses;
        this.usesCount = usesCount;
        this.countryIso2 = countryIso2;
    }

    public UUID getId() {
        return id;
    }

    public String getCodeNormalized() {
        return codeNormalized;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getMaxUses() {
        return maxUses;
    }

    public int getUsesCount() {
        return usesCount;
    }

    public String getCountryIso2() {
        return countryIso2;
    }
}

