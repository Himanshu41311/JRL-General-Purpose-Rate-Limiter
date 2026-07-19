package com.jrl.auth.redis;

import lombok.Builder;
import lombok.Getter;

/**
 * Exact field-for-field match of com.rlaas.ratelimiter.Model.CachedPolicy in
 * the rate limiter proxy — one of these per policy, serialized together as a
 * JSON array at "route-policies:{routeId}". `algorithmConfig` is a JSON
 * string nested inside this JSON (escaped), not a nested object — matching
 * what RateLimitService there expects to parse a second time.
 */
@Getter
@Builder
public class CachedPolicy {
    private String policyId;
    private String routeId;
    private String scope;
    private String identifierSource;
    private String identifierValue;
    private boolean active;
    private String algorithm;
    private String algorithmConfig;
}
