package com.rlaas.ratelimiter.Service;

import com.rlaas.ratelimiter.Entity.Enums.IdentifierSource;
import com.rlaas.ratelimiter.Entity.Enums.Scope;
import com.rlaas.ratelimiter.Model.CachedPolicy;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

/**
 * Extracts the value that identifies "who" is making the request, based
 * on a policy's scope + identifierSource + identifierValue (e.g.
 * scope=API_KEY, identifierSource=HEADER, identifierValue="X-Api-Key").
 *
 * scope/identifierSource arrive as plain strings from Redis (see
 * CachedPolicy) since they're written by an external admin service;
 * invalid values throw IllegalArgumentException, which callers should
 * catch and treat as "skip this malformed policy", not "500 the request".
 */
@Component
public class IdentifierResolver {

    private static final String UNKNOWN = "unknown";

    public String resolve(CachedPolicy policy, ServerHttpRequest request) {

        Scope scope = Scope.valueOf(policy.getScope());

        if (scope == Scope.GLOBAL) {
            return "global";
        }

        if (scope == Scope.IP) {
            return IpResolver.resolve(request);
        }

        if (policy.getIdentifierSource() == null || policy.getIdentifierValue() == null) {
            // Misconfigured policy (USER/API_KEY scope but no source to read from).
            // Fail safe to IP-based limiting rather than let the request through unlimited.
            return IpResolver.resolve(request);
        }

        IdentifierSource source = IdentifierSource.valueOf(policy.getIdentifierSource());

        switch (source) {

            case HEADER:
            case API_KEY:
                String header = request.getHeaders().getFirst(policy.getIdentifierValue());
                return header != null ? header : UNKNOWN;

            case QUERY_PARAM:
                String param = request.getQueryParams().getFirst(policy.getIdentifierValue());
                return param != null ? param : UNKNOWN;

            case COOKIE:
                HttpCookie cookie = request.getCookies().getFirst(policy.getIdentifierValue());
                return cookie != null ? cookie.getValue() : UNKNOWN;

            case IP:
                return IpResolver.resolve(request);

            case PATH_VARIABLE:
                // Proxied routes are opaque target URLs with no route template on our
                // side, so a generic path-variable extraction isn't possible here.
                // Fail safe to IP-based limiting.
                return IpResolver.resolve(request);

            default:
                return UNKNOWN;
        }
    }
}
