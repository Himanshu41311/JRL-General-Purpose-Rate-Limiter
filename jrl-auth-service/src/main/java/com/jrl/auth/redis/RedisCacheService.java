package com.jrl.auth.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jrl.auth.entity.RateLimitPolicy;
import com.jrl.auth.entity.Route;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Writes to Redis whenever a route/policy changes in Postgres — Postgres is
 * still the source of truth (it's what THIS service reads back for its own
 * CRUD), but the rate limiter proxy never touches Postgres, so it only ever
 * sees a route/policy once this class has pushed it here.
 *
 * This is a plain dual-write, not a transaction: if the Redis call fails
 * after Postgres already committed, the two stores disagree until someone
 * notices (there's no outbox/retry/reconciliation job here). That's a real
 * gap — logged loudly on failure so it's at least visible — worth fixing
 * with a periodic reconciliation job or an outbox pattern before this is
 * anything more than an academic project.
 */
@Slf4j
@Service
public class RedisCacheService {

    private static final String ROUTE_KEY_PREFIX = "route:";
    private static final String POLICIES_KEY_PREFIX = "route-policies:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void writeRoute(Route route) {
        CachedRoute payload = CachedRoute.builder()
                .routeId(route.getRouteId().toString())
                .customerId(route.getCustomerId().toString())
                .targetUrl(route.getTargetUrl())
                .active(route.isActive())
                .build();
        try {
            redisTemplate.opsForValue().set(ROUTE_KEY_PREFIX + route.getRouteId(), toJson(payload));
        } catch (Exception e) {
            log.error("Failed to write route {} to Redis — proxy will not see this change until this is retried: {}",
                    route.getRouteId(), e.getMessage());
        }
    }

    public void deleteRoute(UUID routeId) {
        try {
            redisTemplate.delete(ROUTE_KEY_PREFIX + routeId);
        } catch (Exception e) {
            log.error("Failed to delete route {} from Redis — proxy may keep serving it: {}", routeId, e.getMessage());
        }
    }

    /**
     * Rewrites the ENTIRE policy array for a route. The proxy reads all of a
     * route's policies from one key, so there's no such thing as writing a
     * single policy in isolation — every create/update/delete recomputes and
     * overwrites the whole list.
     */
    public void writePolicies(UUID routeId, List<RateLimitPolicy> policies) {
        List<CachedPolicy> payload = policies.stream()
                .map(p -> CachedPolicy.builder()
                        .policyId(p.getPolicyId().toString())
                        .routeId(p.getRouteId().toString())
                        .scope(p.getScope().name())
                        .identifierSource(p.getIdentifierSource() != null ? p.getIdentifierSource().name() : null)
                        .identifierValue(p.getIdentifierValue())
                        .active(p.isActive())
                        .algorithm(p.getAlgorithm().name())
                        .algorithmConfig(p.getAlgorithmConfig())
                        .build())
                .collect(Collectors.toList());
        try {
            redisTemplate.opsForValue().set(POLICIES_KEY_PREFIX + routeId, toJson(payload));
        } catch (Exception e) {
            log.error("Failed to write policies for route {} to Redis — proxy will not see this change until this is retried: {}",
                    routeId, e.getMessage());
        }
    }

    public void deletePolicies(UUID routeId) {
        try {
            redisTemplate.delete(POLICIES_KEY_PREFIX + routeId);
        } catch (Exception e) {
            log.error("Failed to delete policies for route {} from Redis: {}", routeId, e.getMessage());
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            // A serialization failure here is a bug in this class, not bad input —
            // the payload types are fixed POJOs we control.
            throw new IllegalStateException("Failed to serialize Redis payload", e);
        }
    }
}
