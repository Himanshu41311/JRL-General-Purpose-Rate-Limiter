package com.rlaas.ratelimiter.Algorithm;

import com.rlaas.ratelimiter.Entity.Enums.RateLimitAlgorithm;
import com.rlaas.ratelimiter.Model.AlgorithmConfig;
import com.rlaas.ratelimiter.Model.RateLimitResult;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Sliding window log, backed by a Redis sorted set (score = request time
 * in ms). Accurate, no boundary burst issue, at the cost of one ZSET
 * entry per allowed request within the window.
 */
@Component
public class SlidingWindowAlgorithm extends AbstractRateLimitAlgorithm implements RateLimitAlgorithmExecutor {

    private final ReactiveStringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>();
    static {
        SCRIPT.setScriptText(
                "local key = KEYS[1]\n" +
                "local limit = tonumber(ARGV[1])\n" +
                "local window_seconds = tonumber(ARGV[2])\n" +
                "local member = ARGV[3]\n" +
                "\n" +
                "local time = redis.call('TIME')\n" +
                "local now_ms = (tonumber(time[1]) * 1000) + math.floor(tonumber(time[2]) / 1000)\n" +
                "local window_ms = window_seconds * 1000\n" +
                "\n" +
                "redis.call('ZREMRANGEBYSCORE', key, 0, now_ms - window_ms)\n" +
                "local count = redis.call('ZCARD', key)\n" +
                "\n" +
                "if count < limit then\n" +
                "    redis.call('ZADD', key, now_ms, member)\n" +
                "    redis.call('PEXPIRE', key, window_ms)\n" +
                "    return {1, limit - count - 1, window_seconds}\n" +
                "else\n" +
                "    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')\n" +
                "    local reset_ms = window_ms - (now_ms - tonumber(oldest[2]))\n" +
                "    if reset_ms < 0 then reset_ms = 0 end\n" +
                "    return {0, 0, math.ceil(reset_ms / 1000)}\n" +
                "end"
        );
        SCRIPT.setResultType(List.class);
    }

    // Removes exactly the one sorted-set entry evaluate() added — not a blind
    // "remove one member", the precise member string it generated, so a refund
    // can never accidentally remove a different, legitimate request's entry.
    private static final DefaultRedisScript<Long> REFUND_SCRIPT = new DefaultRedisScript<>();
    static {
        REFUND_SCRIPT.setScriptText(
                "local key = KEYS[1]\n" +
                "local member = ARGV[1]\n" +
                "redis.call('ZREM', key, member)\n" +
                "return 1"
        );
        REFUND_SCRIPT.setResultType(Long.class);
    }

    public SlidingWindowAlgorithm(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimitAlgorithm getType() {
        return RateLimitAlgorithm.SLIDING_WINDOW;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<RateLimitResult> evaluate(String key, AlgorithmConfig config) {
        long limit = config.effectiveCapacity();
        long windowSeconds = config.windowSeconds();
        String member = UUID.randomUUID().toString();

        return redisTemplate.execute(
                        SCRIPT,
                        Collections.singletonList(key),
                        List.of(String.valueOf(limit), String.valueOf(windowSeconds), member)
                )
                .next()
                .map(raw -> {
                    List<Object> result = (List<Object>) raw;
                    boolean allowed = asLong(result, 0) == 1;
                    long remaining = Math.max(0, asLong(result, 1));
                    long resetAfter = asLong(result, 2);

                    return RateLimitResult.builder()
                            .allowed(allowed)
                            .limit(limit)
                            .remaining(remaining)
                            .resetAfterSeconds(resetAfter)
                            .retryAfterSeconds(allowed ? 0 : resetAfter)
                            .algorithm(getType().name())
                            // Only set when actually allowed — a denied result never
                            // added an entry, so there'd be nothing to refund anyway.
                            .refundToken(allowed ? member : null)
                            .build();
                });
    }

    @Override
    public Mono<Void> refund(String key, AlgorithmConfig config, String refundToken) {
        if (refundToken == null) {
            return Mono.empty();
        }
        return redisTemplate.execute(REFUND_SCRIPT, Collections.singletonList(key), List.of(refundToken))
                .next()
                .then();
    }
}
