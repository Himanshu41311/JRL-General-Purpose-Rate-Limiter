package com.jrl.auth.service;

import com.jrl.auth.dto.RouteRequest;
import com.jrl.auth.dto.RouteResponse;
import com.jrl.auth.entity.Route;
import com.jrl.auth.exception.InvalidRequestException;
import com.jrl.auth.exception.RouteNotFoundException;
import com.jrl.auth.redis.RedisCacheService;
import com.jrl.auth.redis.RouteHealthService;
import com.jrl.auth.repository.RateLimitPolicyRepository;
import com.jrl.auth.repository.RouteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RouteService {

    private final RouteRepository routeRepository;
    private final RateLimitPolicyRepository policyRepository;
    private final RedisCacheService redisCacheService;
    private final RouteHealthService routeHealthService;

    public RouteService(RouteRepository routeRepository,
                         RateLimitPolicyRepository policyRepository,
                         RedisCacheService redisCacheService,
                         RouteHealthService routeHealthService) {
        this.routeRepository = routeRepository;
        this.policyRepository = policyRepository;
        this.redisCacheService = redisCacheService;
        this.routeHealthService = routeHealthService;
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> list(UUID customerId) {
        return routeRepository.findByCustomerId(customerId).stream()
                .map(route -> RouteResponse.from(route, routeHealthService.isLive(route.getRouteId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RouteResponse get(UUID routeId, UUID customerId) {
        Route route = findOwned(routeId, customerId);
        return RouteResponse.from(route, routeHealthService.isLive(route.getRouteId()));
    }

    @Transactional
    public RouteResponse create(UUID customerId, RouteRequest request) {
        validateTargetUrl(request.getTargetUrl());

        Route route = Route.newRoute(customerId, request.getName().trim(), request.getTargetUrl().trim());
        routeRepository.save(route);

        // Postgres has committed by the time we get here (this method returns before
        // the @Transactional commit, but the save() call itself is flushed to the DB
        // within this transaction) — Redis is written second, deliberately, so a
        // Redis failure never leaves a route "live" in the proxy that doesn't
        // actually exist in the source of truth.
        redisCacheService.writeRoute(route);

        // Re-check Redis rather than assume the write above succeeded — that's the
        // whole point of `live`: it catches exactly this failure mode instead of
        // reporting an optimistic status back to the dashboard.
        return RouteResponse.from(route, routeHealthService.isLive(route.getRouteId()));
    }

    @Transactional
    public RouteResponse update(UUID routeId, UUID customerId, RouteRequest request) {
        validateTargetUrl(request.getTargetUrl());

        Route route = findOwned(routeId, customerId);
        route.setName(request.getName().trim());
        route.setTargetUrl(request.getTargetUrl().trim());
        if (request.getActive() != null) {
            route.setActive(request.getActive());
        }
        route.touch();
        routeRepository.save(route);

        redisCacheService.writeRoute(route);

        return RouteResponse.from(route, routeHealthService.isLive(route.getRouteId()));
    }

    @Transactional
    public void delete(UUID routeId, UUID customerId) {
        Route route = findOwned(routeId, customerId);

        policyRepository.deleteByRouteId(routeId);
        routeRepository.delete(route);

        // Redis cleanup mirrors the Postgres cascade: both keys go, not just one.
        redisCacheService.deletePolicies(routeId);
        redisCacheService.deleteRoute(routeId);
    }

    private Route findOwned(UUID routeId, UUID customerId) {
        // Deliberately the same 404 whether the route doesn't exist at all or
        // belongs to someone else — don't leak which one it is.
        return routeRepository.findByRouteIdAndCustomerId(routeId, customerId)
                .orElseThrow(RouteNotFoundException::new);
    }

    private void validateTargetUrl(String targetUrl) {
        try {
            URI uri = new URI(targetUrl);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null) {
                throw new InvalidRequestException("Target URL must be a full http(s) URL, e.g. https://api.example.com");
            }
        } catch (URISyntaxException e) {
            throw new InvalidRequestException("Target URL is not a valid URL.");
        }
    }
}
