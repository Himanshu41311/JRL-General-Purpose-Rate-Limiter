package com.rlaas.ratelimiter.Algorithm;

import com.rlaas.ratelimiter.Entity.Enums.RateLimitAlgorithm;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves a RateLimitAlgorithm enum value to its executor implementation.
 * Spring autowires every RateLimitAlgorithmExecutor bean into the list;
 * adding a new algorithm is just a new @Component, no switch statement
 * to maintain here.
 */
@Component
public class RateLimitAlgorithmFactory {

    private final Map<RateLimitAlgorithm, RateLimitAlgorithmExecutor> executorsByType;

    public RateLimitAlgorithmFactory(List<RateLimitAlgorithmExecutor> executors) {
        this.executorsByType = executors.stream()
                .collect(Collectors.toMap(RateLimitAlgorithmExecutor::getType, Function.identity()));
    }

    public RateLimitAlgorithmExecutor get(RateLimitAlgorithm algorithm) {
        RateLimitAlgorithmExecutor executor = executorsByType.get(algorithm);
        if (executor == null) {
            throw new IllegalArgumentException("No executor registered for algorithm: " + algorithm);
        }
        return executor;
    }
}
