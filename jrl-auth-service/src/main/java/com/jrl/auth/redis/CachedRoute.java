package com.jrl.auth.redis;

import lombok.Builder;
import lombok.Getter;

/**
 * Exact field-for-field match of com.rlaas.ratelimiter.Model.CachedRoute in
 * the rate limiter proxy — this is what gets serialized to "route:{routeId}".
 * Don't rename fields here without renaming them there too.
 */
@Getter
@Builder
public class CachedRoute {
    private String routeId;
    private String customerId;
    private String targetUrl;
    private boolean active;
}
