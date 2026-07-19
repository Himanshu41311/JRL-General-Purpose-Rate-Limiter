package com.jrl.auth.dto;

import com.jrl.auth.entity.RateLimitPolicy;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PolicyResponse {

    private String policyId;
    private String routeId;
    private String scope;
    private String identifierSource;
    private String identifierValue;
    private boolean active;
    private String algorithm;
    private String algorithmConfig;
    private String createdAt;
    private String updatedAt;

    public static PolicyResponse from(RateLimitPolicy policy) {
        return PolicyResponse.builder()
                .policyId(policy.getPolicyId().toString())
                .routeId(policy.getRouteId().toString())
                .scope(policy.getScope().name())
                .identifierSource(policy.getIdentifierSource() != null ? policy.getIdentifierSource().name() : null)
                .identifierValue(policy.getIdentifierValue())
                .active(policy.isActive())
                .algorithm(policy.getAlgorithm().name())
                .algorithmConfig(policy.getAlgorithmConfig())
                .createdAt(policy.getCreatedAt().toString())
                .updatedAt(policy.getUpdatedAt().toString())
                .build();
    }
}
