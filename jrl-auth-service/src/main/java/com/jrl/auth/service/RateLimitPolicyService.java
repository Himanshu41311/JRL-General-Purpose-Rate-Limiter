package com.jrl.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jrl.auth.dto.PolicyRequest;
import com.jrl.auth.dto.PolicyResponse;
import com.jrl.auth.dto.PolicyUpdateRequest;
import com.jrl.auth.entity.RateLimitPolicy;
import com.jrl.auth.entity.Route;
import com.jrl.auth.entity.enums.Scope;
import com.jrl.auth.exception.InvalidRequestException;
import com.jrl.auth.exception.PolicyNotFoundException;
import com.jrl.auth.exception.RouteNotFoundException;
import com.jrl.auth.redis.RedisCacheService;
import com.jrl.auth.repository.RateLimitPolicyRepository;
import com.jrl.auth.repository.RouteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RateLimitPolicyService {

    private final RateLimitPolicyRepository policyRepository;
    private final RouteRepository routeRepository;
    private final RedisCacheService redisCacheService;
    private final ObjectMapper objectMapper;

    public RateLimitPolicyService(RateLimitPolicyRepository policyRepository,
                                   RouteRepository routeRepository,
                                   RedisCacheService redisCacheService,
                                   ObjectMapper objectMapper) {
        this.policyRepository = policyRepository;
        this.routeRepository = routeRepository;
        this.redisCacheService = redisCacheService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PolicyResponse> list(UUID routeId, UUID customerId) {
        assertRouteOwned(routeId, customerId);
        return policyRepository.findByRouteId(routeId).stream()
                .map(PolicyResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public PolicyResponse create(UUID routeId, UUID customerId, PolicyRequest request) {
        assertRouteOwned(routeId, customerId);
        validateIdentifier(request);
        validateAlgorithmConfig(request.getAlgorithmConfig());

        RateLimitPolicy policy = RateLimitPolicy.newPolicy(
                routeId, request.getScope(), request.getIdentifierSource(),
                request.getIdentifierValue(), request.getAlgorithm(), request.getAlgorithmConfig());
        policyRepository.save(policy);

        resyncRedis(routeId);
        return PolicyResponse.from(policy);
    }

    @Transactional
    public PolicyResponse update(UUID routeId, UUID policyId, UUID customerId, PolicyUpdateRequest request) {
        assertRouteOwned(routeId, customerId);
        validateAlgorithmConfig(request.getAlgorithmConfig());

        RateLimitPolicy policy = policyRepository.findByPolicyIdAndRouteId(policyId, routeId)
                .orElseThrow(PolicyNotFoundException::new);

        // Deliberately NOT touching scope/identifierSource/identifierValue here —
        // see PolicyUpdateRequest's javadoc for why those are create-only.
        policy.setAlgorithm(request.getAlgorithm());
        policy.setAlgorithmConfig(request.getAlgorithmConfig());
        if (request.getActive() != null) {
            policy.setActive(request.getActive());
        }
        policy.touch();
        policyRepository.save(policy);

        resyncRedis(routeId);
        return PolicyResponse.from(policy);
    }

    @Transactional
    public void delete(UUID routeId, UUID policyId, UUID customerId) {
        assertRouteOwned(routeId, customerId);

        RateLimitPolicy policy = policyRepository.findByPolicyIdAndRouteId(policyId, routeId)
                .orElseThrow(PolicyNotFoundException::new);
        policyRepository.delete(policy);

        resyncRedis(routeId);
    }

    /** Rewrites the whole route-policies:{routeId} Redis key from Postgres's current state. */
    private void resyncRedis(UUID routeId) {
        List<RateLimitPolicy> current = policyRepository.findByRouteId(routeId);
        redisCacheService.writePolicies(routeId, current);
    }

    private void assertRouteOwned(UUID routeId, UUID customerId) {
        Route route = routeRepository.findByRouteIdAndCustomerId(routeId, customerId)
                .orElseThrow(RouteNotFoundException::new);
        if (!route.getCustomerId().equals(customerId)) {
            // Defense in depth — findByRouteIdAndCustomerId already filters on this,
            // but a bad ownership check is exactly the kind of bug worth being paranoid about.
            throw new RouteNotFoundException();
        }
    }

    private void validateIdentifier(PolicyRequest request) {
        boolean needsIdentifier = request.getScope() == Scope.USER || request.getScope() == Scope.API_KEY;
        if (needsIdentifier && (request.getIdentifierSource() == null || isBlank(request.getIdentifierValue()))) {
            throw new InvalidRequestException(
                    "identifierSource and identifierValue are required when scope is USER or API_KEY.");
        }
    }

    private void validateAlgorithmConfig(String json) {
        JsonNode node;
        try {
            node = objectMapper.readTree(json);
        } catch (Exception e) {
            throw new InvalidRequestException("algorithmConfig must be valid JSON.");
        }
        if (!node.isObject()) {
            throw new InvalidRequestException("algorithmConfig must be a JSON object.");
        }
        boolean hasCapacityInfo = node.has("limit") || node.has("capacity");
        if (!hasCapacityInfo) {
            throw new InvalidRequestException("algorithmConfig must include either \"limit\" or \"capacity\".");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
