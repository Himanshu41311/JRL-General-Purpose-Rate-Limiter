package com.rlaas.ratelimiter.Service;

import com.rlaas.ratelimiter.Model.CachedPolicy;
import com.rlaas.ratelimiter.Model.CachedRoute;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;

@Service
public class RedisKeyService {

    private final IdentifierResolver identifierResolver;

    public RedisKeyService(IdentifierResolver identifierResolver) {
        this.identifierResolver = identifierResolver;
    }

    /**
     * Builds a Redis key unique per route + policy + identity, e.g.
     * "rl:{routeId}:{policyId}:api_key:abc123"
     *
     * Including the policyId means two policies with the same scope on
     * the same route (e.g. two API_KEY policies with different limits)
     * never collide. This is a *different* Redis keyspace than the
     * "route:*" / "route-policies:*" lookup cache keys.
     */
    public String buildKey(CachedRoute route, CachedPolicy policy, ServerHttpRequest request) {
        String identifier = identifierResolver.resolve(policy, request);

        return "rl:" + route.getRouteId() + ":" + policy.getPolicyId() + ":"
                + policy.getScope().toLowerCase() + ":" + identifier;
    }
}
