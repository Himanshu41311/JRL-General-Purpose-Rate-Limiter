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

    /**
     * Undoes exactly one unit of consumption previously performed by evaluate()
     * on this key — used when a request was allowed by THIS policy but denied
     * by a sibling policy evaluated in parallel on the same request. Without
     * this, a policy that would have allowed a request still permanently loses
     * one unit of its quota for a request that never actually got through.
     *
     * refundToken is whatever evaluate()'s result carried in
     * RateLimitResult.refundToken — only SlidingWindowAlgorithm needs it (the
     * exact sorted-set member to remove); other algorithms ignore it.
     *
     * This is a best-effort compensating action, not a second atomic
     * transaction spanning both calls — there's a brief window between
     * evaluate() consuming and refund() undoing where the key reflects
     * "consumed." A refund failure (e.g. Redis blips) is logged and otherwise
     * accepted; there's no retry queue for it.
     */
    Mono<Void> refund(String key, AlgorithmConfig config, String refundToken);
}
