package com.jrl.auth.entity;

import com.jrl.auth.entity.enums.IdentifierSource;
import com.jrl.auth.entity.enums.RateLimitAlgorithm;
import com.jrl.auth.entity.enums.Scope;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Source of truth in Postgres for one policy attached to one route.
 * `algorithmConfig` is stored as raw JSON text on purpose — same shape the
 * rate limiter proxy expects nested inside the Redis JSON (a JSON string,
 * not a nested object), so no reshaping happens between here and Redis.
 */
@Entity
@Table(name = "rate_limit_policies")
@Getter
@Setter
@NoArgsConstructor
public class RateLimitPolicy {

    @Id
    private UUID policyId;

    @Column(nullable = false)
    private UUID routeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Scope scope;

    @Enumerated(EnumType.STRING)
    private IdentifierSource identifierSource;

    private String identifierValue;

    @Column(nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RateLimitAlgorithm algorithm;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String algorithmConfig;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static RateLimitPolicy newPolicy(UUID routeId, Scope scope, IdentifierSource identifierSource,
                                             String identifierValue, RateLimitAlgorithm algorithm,
                                             String algorithmConfig) {
        RateLimitPolicy policy = new RateLimitPolicy();
        policy.policyId = UUID.randomUUID();
        policy.routeId = routeId;
        policy.scope = scope;
        policy.identifierSource = identifierSource;
        policy.identifierValue = identifierValue;
        policy.active = true;
        policy.algorithm = algorithm;
        policy.algorithmConfig = algorithmConfig;
        Instant now = Instant.now();
        policy.createdAt = now;
        policy.updatedAt = now;
        return policy;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
