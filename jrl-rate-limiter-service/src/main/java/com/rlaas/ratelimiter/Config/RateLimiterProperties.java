package com.rlaas.ratelimiter.Config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rlaas")
public class RateLimiterProperties {

    private final Cache cache = new Cache();
    private final Redis redis = new Redis();

    @Getter
    @Setter
    public static class Cache {
        private long routeTtlSeconds = 300;
        private long policyTtlSeconds = 300;
        private long notFoundTtlSeconds = 5;
    }

    @Getter
    @Setter
    public static class Redis {
        private FailureMode failureMode = FailureMode.CLOSED;
    }

    public enum FailureMode {
        /** Allow requests through, unprotected, while Redis is unavailable. */
        OPEN,
        /** Reject requests while Redis is unavailable. */
        CLOSED
    }
}
