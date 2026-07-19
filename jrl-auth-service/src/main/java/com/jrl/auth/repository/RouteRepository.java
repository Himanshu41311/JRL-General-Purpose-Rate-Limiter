package com.jrl.auth.repository;

import com.jrl.auth.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RouteRepository extends JpaRepository<Route, UUID> {

    List<Route> findByCustomerId(UUID customerId);

    Optional<Route> findByRouteIdAndCustomerId(UUID routeId, UUID customerId);
}
