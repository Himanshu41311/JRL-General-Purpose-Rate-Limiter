package com.jrl.auth.repository;

import com.jrl.auth.entity.RateLimitPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RateLimitPolicyRepository extends JpaRepository<RateLimitPolicy, UUID> {

    List<RateLimitPolicy> findByRouteId(UUID routeId);

    Optional<RateLimitPolicy> findByPolicyIdAndRouteId(UUID policyId, UUID routeId);

    void deleteByRouteId(UUID routeId);
}
