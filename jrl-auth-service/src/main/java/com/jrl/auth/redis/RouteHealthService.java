package com.jrl.auth.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Answers one question, honestly: if a request hit /r/{routeId} on the rate
 * limiter proxy right now, would it find this route? That's a Redis read,
 * not a Postgres read — a route can exist in Postgres and still be "dead"
 * from the proxy's point of view if the dual-write to Redis failed (see
 * RedisCacheService's javadoc on that gap). This is effectively the
 * detector for that failure mode, surfaced as a green/red dot in the UI.
 */
@Slf4j
@Service
public class RouteHealthService {

    private static final String ROUTE_KEY_PREFIX = "route:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RouteHealthService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * True only if "route:{routeId}" exists in Redis AND its cached "active"
     * field is true — i.e., exactly the condition under which the proxy's
     * RouteLookupService.findActiveRoute() would return this route.
     */
    public boolean isLive(UUID routeId) {
        String json;
        try {
            json = redisTemplate.opsForValue().get(ROUTE_KEY_PREFIX + routeId);
        } catch (Exception e) {
            // Can't reach Redis to confirm — don't claim it's live just because
            // we're not sure. A red dot here means "unconfirmed", not necessarily
            // "the proxy is definitely broken", but it's the safer default.
            log.warn("Could not check Redis liveness for route {}: {}", routeId, e.getMessage());
            return false;
        }

        if (json == null) {
            return false;
        }

        try {
            JsonNode node = objectMapper.readTree(json);
            return node.path("active").asBoolean(false);
        } catch (Exception e) {
            log.warn("Unreadable cached route JSON for {}: {}", routeId, e.getMessage());
            return false;
        }
    }
}
