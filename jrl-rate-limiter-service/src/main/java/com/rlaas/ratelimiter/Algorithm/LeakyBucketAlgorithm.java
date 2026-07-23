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
 * Leaky bucket (queue-level variant). A "level" fills by 1 per accepted
 * request and drains continuously at `ratePerSecond`. Requests are
 * rejected once the level would exceed `capacity`. This smooths bursts
 * into a steady outflow, unlike token bucket which lets bursts through
 * immediately.
 */
@Component
public class LeakyBucketAlgorithm extends AbstractRateLimitAlgorithm implements RateLimitAlgorithmExecutor {

    private final ReactiveStringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>();
    static {
        SCRIPT.setScriptText(
                "local key = KEYS[1]\n" +
                "local capacity = tonumber(ARGV[1])\n" +
                "local leak_rate = tonumber(ARGV[2])\n" +
                "\n" +
                "local time = redis.call('TIME')\n" +
                "local now = tonumber(time[1]) + (tonumber(time[2]) / 1000000)\n" +
                "\n" +
                "local data = redis.call('HMGET', key, 'level', 'ts')\n" +
                "local level = tonumber(data[1])\n" +
                "local ts = tonumber(data[2])\n" +
                "\n" +
                "if level == nil then\n" +
                "    level = 0\n" +
                "    ts = now\n" +
                "end\n" +
                "\n" +
                "local elapsed = now - ts\n" +
                "if elapsed < 0 then elapsed = 0 end\n" +
                "level = math.max(0, level - (elapsed * leak_rate))\n" +
                "\n" +
                "local allowed = 0\n" +
                "if level < capacity then\n" +
                "    level = level + 1\n" +
                "    allowed = 1\n" +
                "end\n" +
                "\n" +
                "redis.call('HMSET', key, 'level', level, 'ts', now)\n" +
                "local ttl = math.ceil(capacity / leak_rate) + 1\n" +
                "redis.call('EXPIRE', key, ttl)\n" +
                "\n" +
                "local remaining = math.floor(capacity - level)\n" +
                "local retry_after = 0\n" +
                "if allowed == 0 then\n" +
                "    retry_after = math.ceil((level - capacity + 1) / leak_rate)\n" +
                "end\n" +
                "\n" +
                "return {allowed, remaining, retry_after}"
        );
        SCRIPT.setResultType(List.class);
    }

    public LeakyBucketAlgorithm(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimitAlgorithm getType() {
        return RateLimitAlgorithm.LEAKY_BUCKET;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<RateLimitResult> evaluate(String key, AlgorithmConfig config) {
        long capacity = config.effectiveCapacity();
        double rate = config.effectiveRatePerSecond();

        return redisTemplate.execute(
                        SCRIPT,
                        Collections.singletonList(key),
                        List.of(String.valueOf(capacity), String.valueOf(rate))
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
}
