package com.rlaas.ratelimiter.Algorithm;

import com.rlaas.ratelimiter.Entity.Enums.RateLimitAlgorithm;
import com.rlaas.ratelimiter.Model.AlgorithmConfig;
import com.rlaas.ratelimiter.Model.RateLimitResult;
import reactor.core.publisher.Mono;

public interface RateLimitAlgorithmExecutor {

    RateLimitAlgorithm getType();

    /**
     * Atomically checks and consumes one unit of quota for the given key.
     * Non-blocking: backed by ReactiveStringRedisTemplate, so this never
     * parks a thread waiting on Redis.
     */
    Mono<RateLimitResult> evaluate(String key, AlgorithmConfig config);
}
