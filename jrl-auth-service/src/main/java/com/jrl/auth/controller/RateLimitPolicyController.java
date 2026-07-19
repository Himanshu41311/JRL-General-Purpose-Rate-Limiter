package com.jrl.auth.controller;

import com.jrl.auth.dto.PolicyRequest;
import com.jrl.auth.dto.PolicyResponse;
import com.jrl.auth.dto.PolicyUpdateRequest;
import com.jrl.auth.security.AuthenticatedUser;
import com.jrl.auth.service.RateLimitPolicyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/routes/{routeId}/policies")
public class RateLimitPolicyController {

    private final RateLimitPolicyService policyService;

    public RateLimitPolicyController(RateLimitPolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    public ResponseEntity<List<PolicyResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                       @PathVariable UUID routeId) {
        return ResponseEntity.ok(policyService.list(routeId, principal.customerId()));
    }

    @PostMapping
    public ResponseEntity<PolicyResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @PathVariable UUID routeId,
                                                  @Valid @RequestBody PolicyRequest request) {
        return ResponseEntity.ok(policyService.create(routeId, principal.customerId(), request));
    }

    @PutMapping("/{policyId}")
    public ResponseEntity<PolicyResponse> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @PathVariable UUID routeId,
                                                  @PathVariable UUID policyId,
                                                  @Valid @RequestBody PolicyUpdateRequest request) {
        return ResponseEntity.ok(policyService.update(routeId, policyId, principal.customerId(), request));
    }

    @DeleteMapping("/{policyId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                        @PathVariable UUID routeId,
                                        @PathVariable UUID policyId) {
        policyService.delete(routeId, policyId, principal.customerId());
        return ResponseEntity.noContent().build();
    }
}
