package com.jrl.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Source of truth in Postgres. Every create/update/delete here triggers a
 * matching write to Redis (see RedisCacheService) so the rate limiter proxy
 * sees the change — but Postgres, not Redis, is what this service itself
 * reads and writes for anything except that dual-write.
 */
@Entity
@Table(name = "routes")
@Getter
@Setter
@NoArgsConstructor
public class Route {

    @Id
    private UUID routeId;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 2048)
    private String targetUrl;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static Route newRoute(UUID customerId, String name, String targetUrl) {
        Route route = new Route();
        route.routeId = UUID.randomUUID();
        route.customerId = customerId;
        route.name = name;
        route.targetUrl = targetUrl;
        route.active = true;
        Instant now = Instant.now();
        route.createdAt = now;
        route.updatedAt = now;
        return route;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
