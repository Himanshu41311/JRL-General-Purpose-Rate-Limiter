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

/**
 * Fixed window counter.
 *
 * Simple and cheap, but allows up to 2x the limit at window boundaries
 * (e.g. a burst at 0:59 and another at 1:00). Prefer SLIDING_WINDOW when
 * that matters.
 */
@Component
public class FixedWindowAlgorithm extends AbstractRateLimitAlgorithm implements RateLimitAlgorithmExecutor {

    private final ReactiveStringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>();
    static {
        SCRIPT.setScriptText(
                "local key = KEYS[1]\n" +
                "local limit = tonumber(ARGV[1])\n" +
                "local window_seconds = tonumber(ARGV[2])\n" +
                "\n" +
                "local current = redis.call('INCR', key)\n" +
                "if current == 1 then\n" +
                "    redis.call('EXPIRE', key, window_seconds)\n" +
                "end\n" +
                "\n" +
                "local ttl = redis.call('TTL', key)\n" +
                "if ttl < 0 then\n" +
                "    ttl = window_seconds\n" +
                "end\n" +
                "\n" +
                "if current > limit then\n" +
                "    return {0, 0, ttl}\n" +
                "else\n" +
                "    return {1, limit - current, ttl}\n" +
                "end"
        );
        SCRIPT.setResultType(List.class);
    }

    // DECR (and INCRBY) never touch a key's TTL — only EXPIRE/SET do — so this
    // is safe to run without worrying about resetting the window's expiry.
    // Floors at 0 rather than going negative if, e.g., the key already expired
    // between evaluate() and this refund landing.
    private static final DefaultRedisScript<Long> REFUND_SCRIPT = new DefaultRedisScript<>();
    static {
        REFUND_SCRIPT.setScriptText(
                "local key = KEYS[1]\n" +
                "local current = redis.call('DECR', key)\n" +
                "if current < 0 then\n" +
                "    redis.call('SET', key, 0, 'KEEPTTL')\n" +
                "end\n" +
                "return 1"
        );
        REFUND_SCRIPT.setResultType(Long.class);
    }

    public FixedWindowAlgorithm(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimitAlgorithm getType() {
        return RateLimitAlgorithm.FIXED_WINDOW;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<RateLimitResult> evaluate(String key, AlgorithmConfig config) {
        long limit = config.effectiveCapacity();
        long windowSeconds = config.windowSeconds();

        return redisTemplate.execute(
                        SCRIPT,
                        Collections.singletonList(key),
                        List.of(String.valueOf(limit), String.valueOf(windowSeconds))
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
                            .build();
                });
    }

    @Override
    public Mono<Void> refund(String key, AlgorithmConfig config, String refundToken) {
        return redisTemplate.execute(REFUND_SCRIPT, Collections.singletonList(key), List.of())
                .next()
                .then();
    }
}
