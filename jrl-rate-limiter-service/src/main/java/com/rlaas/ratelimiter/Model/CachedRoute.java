package com.rlaas.ratelimiter.Model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The shape this service reads from Redis at key "route:{routeId}" (JSON string).
 *
 * This is the read-side contract with the admin/registration service: whenever a
 * customer creates or updates a route there, it should write (or overwrite) this
 * key so every instance of this proxy sees the change immediately, with no
 * per-instance staleness window. Postgres remains the source of truth on the
 * admin side; this service never talks to Postgres.
 *
 * Example value:
 * {"routeId":"...", "customerId":"...", "targetUrl":"https://api.example.com/v1",
 *  "active": true}
 */
@Getter
@Setter
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CachedRoute {

    private String routeId;
    private String customerId;
    private String targetUrl;
    private boolean active;

    // All-args constructor for @Builder, kept explicit so Jackson + Lombok don't fight
    public CachedRoute(String routeId, String customerId, String targetUrl, boolean active) {
        this.routeId = routeId;
        this.customerId = customerId;
        this.targetUrl = targetUrl;
        this.active = active;
    }
}
