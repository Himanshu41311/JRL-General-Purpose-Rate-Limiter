package com.jrl.auth.controller;

import com.jrl.auth.dto.RouteRequest;
import com.jrl.auth.dto.RouteResponse;
import com.jrl.auth.dto.RouteStatusResponse;
import com.jrl.auth.redis.RouteHealthService;
import com.jrl.auth.security.AuthenticatedUser;
import com.jrl.auth.service.RouteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteService routeService;
    private final RouteHealthService routeHealthService;

    public RouteController(RouteService routeService, RouteHealthService routeHealthService) {
        this.routeService = routeService;
        this.routeHealthService = routeHealthService;
    }

    @GetMapping
    public ResponseEntity<List<RouteResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(routeService.list(principal.customerId()));
    }

    @GetMapping("/{routeId}")
    public ResponseEntity<RouteResponse> get(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @PathVariable UUID routeId) {
        return ResponseEntity.ok(routeService.get(routeId, principal.customerId()));
    }

    /**
     * Lighter-weight than GET /{routeId} for a dashboard that just wants to
     * refresh the green/red dot on a timer. Still enforces ownership —
     * routeService.get() throws a 404 if this route isn't yours or doesn't
     * exist — before reporting the Redis-backed live status.
     */
    @GetMapping("/{routeId}/status")
    public ResponseEntity<RouteStatusResponse> status(@AuthenticationPrincipal AuthenticatedUser principal,
                                                        @PathVariable UUID routeId) {
        routeService.get(routeId, principal.customerId()); // ownership check; throws 404 if not yours

        return ResponseEntity.ok(RouteStatusResponse.builder()
                .routeId(routeId.toString())
                .live(routeHealthService.isLive(routeId))
                .checkedAt(Instant.now().toString())
                .build());
    }

    @PostMapping
    public ResponseEntity<RouteResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @Valid @RequestBody RouteRequest request) {
        return ResponseEntity.ok(routeService.create(principal.customerId(), request));
    }

    @PutMapping("/{routeId}")
    public ResponseEntity<RouteResponse> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @PathVariable UUID routeId,
                                                 @Valid @RequestBody RouteRequest request) {
        return ResponseEntity.ok(routeService.update(routeId, principal.customerId(), request));
    }

    @DeleteMapping("/{routeId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                        @PathVariable UUID routeId) {
        routeService.delete(routeId, principal.customerId());
        return ResponseEntity.noContent().build();
    }
}
