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
 * Token bucket: bucket refills continuously at `ratePerSecond`, capped at
 * `capacity`. Each request consumes 1 token. Allows short bursts up to
 * the full capacity, unlike fixed/sliding window.
 */
@Component
public class TokenBucketAlgorithm extends AbstractRateLimitAlgorithm implements RateLimitAlgorithmExecutor {

    private final ReactiveStringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>();
    static {
        SCRIPT.setScriptText(
                "local key = KEYS[1]\n" +
                "local capacity = tonumber(ARGV[1])\n" +
                "local rate = tonumber(ARGV[2])\n" +
                "local requested = tonumber(ARGV[3])\n" +
                "\n" +
                "local time = redis.call('TIME')\n" +
                "local now = tonumber(time[1]) + (tonumber(time[2]) / 1000000)\n" +
                "\n" +
                "local data = redis.call('HMGET', key, 'tokens', 'ts')\n" +
                "local tokens = tonumber(data[1])\n" +
                "local ts = tonumber(data[2])\n" +
                "\n" +
                "if tokens == nil then\n" +
                "    tokens = capacity\n" +
                "    ts = now\n" +
                "end\n" +
                "\n" +
                "local elapsed = now - ts\n" +
                "if elapsed < 0 then elapsed = 0 end\n" +
                "tokens = math.min(capacity, tokens + (elapsed * rate))\n" +
                "\n" +
                "local allowed = 0\n" +
                "if tokens >= requested then\n" +
                "    tokens = tokens - requested\n" +
                "    allowed = 1\n" +
                "end\n" +
                "\n" +
                "redis.call('HMSET', key, 'tokens', tokens, 'ts', now)\n" +
                "local ttl = math.ceil(capacity / rate) + 1\n" +
                "redis.call('EXPIRE', key, ttl)\n" +
                "\n" +
                "local retry_after = 0\n" +
                "if allowed == 0 then\n" +
                "    retry_after = math.ceil((requested - tokens) / rate)\n" +
                "end\n" +
                "\n" +
                "return {allowed, math.floor(tokens), retry_after}"
        );
        SCRIPT.setResultType(List.class);
    }

    // Hands back 1 token, capped at capacity (in case a real refill already
    // pushed tokens close to full between evaluate() and this landing).
    // Deliberately does NOT touch 'ts' — that's the refill clock, and nudging
    // it would let a refund also grant extra un-earned refill time.
    private static final DefaultRedisScript<Long> REFUND_SCRIPT = new DefaultRedisScript<>();
    static {
        REFUND_SCRIPT.setScriptText(
                "local key = KEYS[1]\n" +
                "local capacity = tonumber(ARGV[1])\n" +
                "\n" +
                "local data = redis.call('HMGET', key, 'tokens')\n" +
                "local tokens = tonumber(data[1])\n" +
                "if tokens == nil then\n" +
                "    return 0\n" +
                "end\n" +
                "\n" +
                "tokens = math.min(capacity, tokens + 1)\n" +
                "redis.call('HSET', key, 'tokens', tokens)\n" +
                "return 1"
        );
        REFUND_SCRIPT.setResultType(Long.class);
    }

    public TokenBucketAlgorithm(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimitAlgorithm getType() {
        return RateLimitAlgorithm.TOKEN_BUCKET;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<RateLimitResult> evaluate(String key, AlgorithmConfig config) {
        long capacity = config.effectiveCapacity();
        double rate = config.effectiveRatePerSecond();

        return redisTemplate.execute(
                        SCRIPT,
                        Collections.singletonList(key),
                        List.of(String.valueOf(capacity), String.valueOf(rate), "1")
                )
                .next()
                .map(raw -> {
                    List<Object> result = (List<Object>) raw;
                    boolean allowed = asLong(result, 0) == 1;
                    long remaining = Math.max(0, asLong(result, 1));
                    long retryAfter = asLong(result, 2);

                    return RateLimitResult.builder()
                            .allowed(allowed)
                            .limit(capacity)
                            .remaining(remaining)
                            .resetAfterSeconds(retryAfter)
                            .retryAfterSeconds(retryAfter)
                            .algorithm(getType().name())
                            .build();
                });
    }

    @Override
    public Mono<Void> refund(String key, AlgorithmConfig config, String refundToken) {
        long capacity = config.effectiveCapacity();
        return redisTemplate.execute(REFUND_SCRIPT, Collections.singletonList(key), List.of(String.valueOf(capacity)))
                .next()
                .then();
    }
}
