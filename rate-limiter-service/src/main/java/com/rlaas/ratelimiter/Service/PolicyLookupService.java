package com.rlaas.ratelimiter.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.rlaas.ratelimiter.Model.CachedPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Reads policies straight from Redis (key "route-policies:{routeId}",
 * a JSON array). Same rationale as RouteLookupService: Redis is the shared
 * cache across every instance of this service, Postgres is never touched
 * here, and the admin service owns keeping this key in sync.
 */
@Slf4j
@Service
public class PolicyLookupService {

    private static final String POLICIES_KEY_PREFIX = "route-policies:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PolicyLookupService(ReactiveStringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Never empty — returns an empty list if the key is missing or unparsable
     * (logged), so callers can treat "no policies" uniformly.
     */
    public Mono<List<CachedPolicy>> findPoliciesForRoute(UUID routeId) {
        return redisTemplate.opsForValue()
                .get(POLICIES_KEY_PREFIX + routeId)
                .flatMap(this::parse)
                .defaultIfEmpty(Collections.emptyList());
    }

    private Mono<List<CachedPolicy>> parse(String json) {
        try {
            List<CachedPolicy> policies = objectMapper.readValue(json, new TypeReference<List<CachedPolicy>>() {});
            return Mono.just(policies);
        } catch (Exception e) {
            log.error("Failed to parse cached policies JSON: {}", e.getMessage());
            return Mono.empty();
        }
    }
}
