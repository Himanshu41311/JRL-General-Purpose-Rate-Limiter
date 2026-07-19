package com.jrl.auth.dto;

import com.jrl.auth.entity.enums.RateLimitAlgorithm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Deliberately narrower than PolicyRequest (used for creation). scope,
 * identifierSource, and identifierValue are baked into the Redis counter
 * key ("rl:{routeId}:{policyId}:{scope}:{identifier}") — changing them in
 * place would silently orphan whatever counter was accruing under the old
 * key. If you need to change who a policy applies to, delete it and create
 * a new one; editing an existing policy is only ever allowed to change the
 * algorithm, its config values, and whether it's active.
 */
@Getter
@Setter
public class PolicyUpdateRequest {

    @NotNull(message = "Algorithm is required")
    private RateLimitAlgorithm algorithm;

    @NotBlank(message = "algorithmConfig is required")
    private String algorithmConfig;

    // Optional — null means "leave active status unchanged".
    private Boolean active;
}
