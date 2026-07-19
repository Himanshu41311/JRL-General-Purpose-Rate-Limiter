package com.rlaas.ratelimiter.Model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class RateLimitResult {

    private boolean allowed;
    private long limit;
    private long remaining;
    private long resetAfterSeconds;
    private long retryAfterSeconds;
    private String policyId;
    private String algorithm;

    /**
     * True when this result was NOT actually computed by an algorithm — Redis was
     * unavailable / the circuit breaker was open, and this is the configured
     * fallback (fail-open OR fail-closed, see RateLimiterProperties). Kept separate
     * from `allowed` and `algorithm` so metrics/logs can flag "rate limiting was
     * bypassed/degraded" distinctly from a normal decision either way.
     */
    @Builder.Default
    private boolean redisUnavailable = false;
}
