package com.jrl.auth.dto;

import com.jrl.auth.entity.Route;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RouteResponse {

    private String routeId;
    private String customerId;
    private String name;
    private String targetUrl;

    // The Postgres-side setting: "should this route be enabled".
    private boolean active;

    // Whether /r/{routeId} would actually work right now, per Redis — the
    // green/red dot in the dashboard. Can be false even when active=true,
    // if the dual-write to Redis failed after this was saved to Postgres.
    private boolean live;

    private String createdAt;
    private String updatedAt;

    public static RouteResponse from(Route route, boolean live) {
        return RouteResponse.builder()
                .routeId(route.getRouteId().toString())
                .customerId(route.getCustomerId().toString())
                .name(route.getName())
                .targetUrl(route.getTargetUrl())
                .active(route.isActive())
                .live(live)
                .createdAt(route.getCreatedAt().toString())
                .updatedAt(route.getUpdatedAt().toString())
                .build();
    }
}
