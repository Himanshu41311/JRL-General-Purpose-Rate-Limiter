package com.rlaas.ratelimiter.Algorithm;

import java.util.List;

abstract class AbstractRateLimitAlgorithm {

    protected long asLong(List<Object> result, int index) {
        return ((Number) result.get(index)).longValue();
    }
}
