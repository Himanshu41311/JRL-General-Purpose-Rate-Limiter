package com.rlaas.ratelimiter.Model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rlaas.ratelimiter.Entity.Enums.WindowUnit;
import lombok.Getter;
import lombok.Setter;

/**
 * Parsed shape of RateLimitPolicy.algorithmConfig (stored as jsonb).
 *
 * Fixed / sliding window use: limit + windowSize + windowUnit
 *   e.g. {"limit": 100, "windowSize": 1, "windowUnit": "MINUTE"}
 *
 * Token / leaky bucket use: capacity + either ratePerSecond directly,
 * or limit + windowSize + windowUnit to derive a rate.
 *   e.g. {"capacity": 50, "ratePerSecond": 5}
 *   or   {"limit": 300, "windowSize": 1, "windowUnit": "MINUTE"}  (-> 5 req/s)
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlgorithmConfig {

    private Long limit;
    private Integer windowSize;
    private WindowUnit windowUnit;
    private Long capacity;
    private Double ratePerSecond;

    /**
     * Converts windowSize + windowUnit into a plain seconds value that the
     * Lua scripts can use directly.
     *
     * @return the configured window length in seconds
     * @throws IllegalStateException if windowSize or windowUnit is missing
     */
    public long windowSeconds() {
        if (windowSize == null || windowUnit == null) {
            throw new IllegalStateException(
                    "windowSize and windowUnit are required in algorithmConfig");
        }
        return (long) windowSize * unitSeconds(windowUnit);
    }

    /**
     * Resolves the bucket capacity / window limit to use, preferring an
     * explicit `capacity` over `limit` when both are present.
     *
     * @return the effective capacity (bucket size, or fixed/sliding window limit)
     * @throws IllegalStateException if neither `capacity` nor `limit` is set
     */
    public long effectiveCapacity() {
        if (capacity != null) return capacity;
        if (limit != null) return limit;
        throw new IllegalStateException(
                "Either 'capacity' or 'limit' must be set in algorithmConfig");
    }

    /**
     * Resolves the refill/leak rate to use for token/leaky bucket, preferring
     * an explicit `ratePerSecond` over one derived from `limit` / window.
     *
     * @return the effective rate in requests-per-second
     * @throws IllegalStateException if there isn't enough info to derive a rate
     */
    public double effectiveRatePerSecond() {
        if (ratePerSecond != null) return ratePerSecond;
        if (limit != null) {
            long seconds = windowSeconds();
            if (seconds <= 0) {
                throw new IllegalStateException("windowSeconds must be positive");
            }
            return (double) limit / (double) seconds;
        }
        throw new IllegalStateException(
                "Either 'ratePerSecond' or ('limit' + 'windowSize' + 'windowUnit') must be set");
    }

    /**
     * @param unit the configured window unit
     * @return how many seconds one unit of `windowUnit` represents
     * @throws IllegalArgumentException if the unit isn't one of the known values
     */
    private static long unitSeconds(WindowUnit unit) {
        switch (unit) {
            case SECOND: return 1L;
            case MINUTE: return 60L;
            case HOUR: return 3600L;
            case DAY: return 86400L;
            default: throw new IllegalArgumentException("Unsupported window unit: " + unit);
        }
    }
}
