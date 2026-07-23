package com.rlaas.ratelimiter.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.rlaas.ratelimiter.Config.RateLimiterProperties;
import com.rlaas.ratelimiter.Model.CachedRoute;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Reads Route data straight from Redis (key "route:{routeId}") — Redis IS the
 * cache here, and because every instance of this service reads the same
 * Redis, there's no per-instance staleness window the way a local Caffeine
 * cache would have. Postgres is never touched by this service at request time;
 * the admin/registration service owns writing this key whenever a route
 * changes.
 *
 * On top of that: a small local Caffeine cache remembers *only* "this
 * routeId doesn't exist" for rlaas.cache.not-found-ttl-seconds, so someone
 * scanning random UUIDs against /r/{routeId} can't force a Redis round trip
 * per guess. Real routes are never held in this local cache, so a route
 * created moments ago is visible immediately.
 *
 * Note: RateLimiterProperties also exposes route-ttl-seconds / policy-ttl-seconds,
 * but they're intentionally NOT used here — a local TTL cache for *found* routes
 * would reintroduce the exact per-instance staleness problem this design avoids.
 * They're reserved in case a future version adds an optional local layer on top
 * of Redis for extreme-throughput routes.
 */
@Slf4j
@Service
public class RouteLookupService {

    private static final String ROUTE_KEY_PREFIX = "route:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Cache<UUID, Boolean> notFoundCache;

    public RouteLookupService(ReactiveStringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper,
                               RateLimiterProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.notFoundCache = Caffeine.newBuilder()
                .expireAfterWrite(properties.getCache().getNotFoundTtlSeconds(), TimeUnit.SECONDS)
                .maximumSize(50_000)
                .build();
    }

    /**
     * Empty Mono means "no active route with this id" — either it doesn't
     * exist, it's inactive, or the cached JSON was unreadable (logged).
     */
    public Mono<CachedRoute> findActiveRoute(UUID routeId) {

        if (Boolean.TRUE.equals(notFoundCache.getIfPresent(routeId))) {
            return Mono.empty();
        }

        return redisTemplate.opsForValue()
                .get(ROUTE_KEY_PREFIX + routeId)
                .flatMap(this::parse)
                .filter(CachedRoute::isActive)
                .switchIfEmpty(Mono.fromRunnable(() -> notFoundCache.put(routeId, Boolean.TRUE)));
    }

    private Mono<CachedRoute> parse(String json) {
        try {
            return Mono.justOrEmpty(objectMapper.readValue(json, CachedRoute.class));
        } catch (Exception e) {
            log.error("Failed to parse cached route JSON: {}", e.getMessage());
            return Mono.empty();
        }
    }
}
