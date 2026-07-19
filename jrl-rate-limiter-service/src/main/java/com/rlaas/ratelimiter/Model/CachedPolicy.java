package com.rlaas.ratelimiter.Model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The shape this service reads from Redis at key "route-policies:{routeId}"
 * (a JSON array of these). Same contract note as CachedRoute: the admin
 * service owns writing this whenever a policy is created/updated/deleted.
 *
 * scope / identifierSource / algorithm are plain strings here (not the JPA
 * enums) so a malformed value from the admin side fails one policy's
 * evaluation with a logged warning instead of a JSON deserialization
 * exception that could take down the whole lookup.
 *
 * Example value:
 * [{"policyId":"...", "routeId":"...", "scope":"API_KEY",
 *   "identifierSource":"HEADER", "identifierValue":"X-Api-Key",
 *   "active": true, "algorithm":"TOKEN_BUCKET",
 *   "algorithmConfig":"{\"capacity\":50,\"ratePerSecond\":5}"}]
 */
@Getter
@Setter
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CachedPolicy {

    private String policyId;
    private String routeId;
    private String scope;
    private String identifierSource;
    private String identifierValue;
    private boolean active;
    private String algorithm;
    private String algorithmConfig;

    public CachedPolicy(String policyId, String routeId, String scope, String identifierSource,
                         String identifierValue, boolean active, String algorithm, String algorithmConfig) {
        this.policyId = policyId;
        this.routeId = routeId;
        this.scope = scope;
        this.identifierSource = identifierSource;
        this.identifierValue = identifierValue;
        this.active = active;
        this.algorithm = algorithm;
        this.algorithmConfig = algorithmConfig;
    }
}
