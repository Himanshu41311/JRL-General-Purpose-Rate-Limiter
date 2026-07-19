package com.jrl.auth.entity.enums;

/** Must match com.rlaas.ratelimiter.Entity.Enums.RateLimitAlgorithm in the rate limiter proxy exactly. */
public enum RateLimitAlgorithm {
    FIXED_WINDOW, SLIDING_WINDOW, TOKEN_BUCKET, LEAKY_BUCKET
}
