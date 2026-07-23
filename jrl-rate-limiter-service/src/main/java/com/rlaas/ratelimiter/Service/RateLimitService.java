package com.rlaas.ratelimiter.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rlaas.ratelimiter.Algorithm.RateLimitAlgorithmExecutor;
import com.rlaas.ratelimiter.Algorithm.RateLimitAlgorithmFactory;
import com.rlaas.ratelimiter.Config.RateLimiterProperties;
import com.rlaas.ratelimiter.Entity.Enums.RateLimitAlgorithm;
import com.rlaas.ratelimiter.Model.AlgorithmConfig;
import com.rlaas.ratelimiter.Model.CachedPolicy;
import com.rlaas.ratelimiter.Model.CachedRoute;
import com.rlaas.ratelimiter.Model.RateLimitResult;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Evaluates every active policy attached to a route and enforces
 * "most restrictive policy wins": the request is denied if ANY policy
 * denies it.
 *
 * All-reactive: policies are evaluated in parallel (independent Redis
 * calls fired concurrently via flatMap, not one-at-a-time), and every
 * call is wrapped by a circuit breaker so a Redis outage degrades
 * predictably instead of hanging every request or throwing raw
 * connection errors.
 *
 * IMPORTANT — the refund step below: because policies are evaluated in
 * parallel, a policy that WOULD allow a request still consumes one unit of
 * its quota the instant its own Lua script runs — before this class has any
 * idea whether a sibling policy is about to deny the same request. Left
 * alone, that's a real leak: every denied request silently drains quota
 * from whichever policies would have allowed it. So once the final decision
 * is known, if it's a denial, every policy that was individually allowed
 * gets a compensating refund() call to undo its consumption. This is a
 * best-effort correction, not a second atomic transaction spanning all
 * policies — there's a brief window where a counter reflects "consumed"
 * before the refund lands, and a refund itself can fail (logged, not
 * retried). The alternative (peek-then-commit across two Redis round trips
 * per policy, even when everything would've been allowed) was rejected as
 * not worth doubling latency on the common path for this.
 *
 * Redis failure behavior (fail-open vs fail-closed) is configurable via
 * `rlaas.redis.failure-mode` (see RateLimiterProperties) rather than
 * hardcoded — default is CLOSED (deny traffic) since silently letting
 * every customer's requests through unlimited is the riskier default to
 * ship with. Flip to OPEN once you've decided that's the right tradeoff
 * for your traffic. Either way, the result is tagged redisUnavailable=true so
 * ProxyService can log/count it distinctly from a normal decision — and so
 * the refund step below knows never to refund it (nothing real was
 * consumed in the first place).
 */
@Slf4j
@Service
public class RateLimitService {

    private static final String CIRCUIT_BREAKER_NAME = "redis";

    private final RedisKeyService redisKeyService;
    private final RateLimitAlgorithmFactory algorithmFactory;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker circuitBreaker;
    private final MeterRegistry meterRegistry;
    private final RateLimiterProperties properties;

    public RateLimitService(RedisKeyService redisKeyService,
                             RateLimitAlgorithmFactory algorithmFactory,
                             ObjectMapper objectMapper,
                             CircuitBreakerRegistry circuitBreakerRegistry,
                             MeterRegistry meterRegistry,
                             RateLimiterProperties properties) {
        this.redisKeyService = redisKeyService;
        this.algorithmFactory = algorithmFactory;
        this.objectMapper = objectMapper;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);
        this.meterRegistry = meterRegistry;
        this.properties = properties;
    }

    public Mono<RateLimitResult> evaluate(CachedRoute route, List<CachedPolicy> policies, ServerHttpRequest request) {

        List<CachedPolicy> activePolicies = policies.stream()
                .filter(CachedPolicy::isActive)
                .toList();

        if (activePolicies.isEmpty()) {
            return Mono.just(allowDefault());
        }

        return Flux.fromIterable(activePolicies)
                .flatMap(policy -> evaluateSinglePolicy(route, policy, request))
                .collectList()
                .flatMap(evaluations -> finalizeDecision(route, evaluations));
    }

    /**
     * Carries everything needed to issue a refund later (executor, key,
     * config) alongside the result itself — evaluate() doesn't know yet
     * whether this policy's "allowed" will end up mattering.
     */
    private record PolicyEvaluation(RateLimitAlgorithmExecutor executor, String key,
                                     AlgorithmConfig config, RateLimitResult result) {
    }

    private Mono<PolicyEvaluation> evaluateSinglePolicy(CachedRoute route, CachedPolicy policy, ServerHttpRequest request) {

        AlgorithmConfig config;
        RateLimitAlgorithmExecutor executor;
        String key;
        try {
            config = objectMapper.readValue(policy.getAlgorithmConfig(), AlgorithmConfig.class);
            executor = algorithmFactory.get(RateLimitAlgorithm.valueOf(policy.getAlgorithm()));
            key = redisKeyService.buildKey(route, policy, request);
        } catch (Exception e) {
            // Malformed policy (bad JSON, unknown enum value, etc). Skip it rather than
            // let one bad row from the admin service take down every request on this route.
            log.error("Skipping malformed policy {} on route {}: {}", policy.getPolicyId(), route.getRouteId(), e.getMessage());
            return Mono.empty();
        }

        RateLimitAlgorithmExecutor finalExecutor = executor;
        String finalKey = key;
        AlgorithmConfig finalConfig = config;

        return executor.evaluate(key, config)
                .map(result -> result.toBuilder().policyId(policy.getPolicyId()).build())
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .map(result -> new PolicyEvaluation(finalExecutor, finalKey, finalConfig, result))
                .onErrorResume(ex -> {
                    meterRegistry.counter("rate_limiter_redis_failures_total",
                                    "customerId", nullToUnknown(route.getCustomerId()),
                                    "failureMode", properties.getRedis().getFailureMode().name())
                            .increment();

                    boolean allowOnFailure = properties.getRedis().getFailureMode() == RateLimiterProperties.FailureMode.OPEN;
                    log.error("Redis unavailable evaluating policy {} on route {}, failing {}: {}",
                            policy.getPolicyId(), route.getRouteId(),
                            allowOnFailure ? "open" : "closed", ex.toString());

                    RateLimitResult fallback = allowOnFailure ? failOpenResult(policy) : failClosedResult(policy);
                    // executor/key/config are still carried here even though nothing was
                    // actually consumed — harmless, because redisUnavailable=true on this
                    // result makes finalizeDecision() skip it during the refund pass.
                    return Mono.just(new PolicyEvaluation(finalExecutor, finalKey, finalConfig, fallback));
                });
    }

    private Mono<RateLimitResult> finalizeDecision(CachedRoute route, List<PolicyEvaluation> evaluations) {

        if (evaluations.isEmpty()) {
            return Mono.just(allowDefault());
        }

        RateLimitResult decision = reduce(evaluations.stream().map(PolicyEvaluation::result).toList());

        if (decision.isAllowed()) {
            return Mono.just(decision);
        }

        // Denied overall — every policy that individually said "allowed" and actually
        // consumed something real (not a redisUnavailable fallback) needs that
        // consumption undone, or it's permanently lost for a request that never
        // actually got through. See this class's javadoc for why this can't simply
        // be avoided by checking before consuming.
        List<PolicyEvaluation> toRefund = evaluations.stream()
                .filter(e -> e.result().isAllowed() && !e.result().isRedisUnavailable())
                .toList();

        if (toRefund.isEmpty()) {
            return Mono.just(decision);
        }

        return Flux.fromIterable(toRefund)
                .flatMap(e -> e.executor().refund(e.key(), e.config(), e.result().getRefundToken())
                        .onErrorResume(ex -> {
                            log.error("Failed to refund policy {} on route {} after a sibling policy denied the request: {}",
                                    e.result().getPolicyId(), route.getRouteId(), ex.toString());
                            return Mono.empty();
                        }))
                .then(Mono.just(decision));
    }

    private RateLimitResult reduce(List<RateLimitResult> results) {

        return results.stream()
                .filter(r -> !r.isAllowed())
                .findFirst()
                .orElseGet(() -> results.stream()
                        .min(Comparator.comparingLong(RateLimitResult::getRemaining))
                        .orElseGet(this::allowDefault));
    }

    private RateLimitResult allowDefault() {
        return RateLimitResult.builder()
                .allowed(true)
                .limit(-1)
                .remaining(-1)
                .resetAfterSeconds(0)
                .retryAfterSeconds(0)
                .algorithm("NONE")
                .build();
    }

    private RateLimitResult failOpenResult(CachedPolicy policy) {
        return RateLimitResult.builder()
                .allowed(true)
                .limit(-1)
                .remaining(-1)
                .resetAfterSeconds(0)
                .retryAfterSeconds(0)
                .policyId(policy.getPolicyId())
                .algorithm("REDIS_UNAVAILABLE_FAIL_OPEN")
                .redisUnavailable(true)
                .build();
    }

    private RateLimitResult failClosedResult(CachedPolicy policy) {
        return RateLimitResult.builder()
                .allowed(false)
                .limit(0)
                .remaining(0)
                .resetAfterSeconds(0)
                // Short, fixed retry hint — we don't know when Redis will recover,
                // this just avoids clients hammering us in a tight retry loop.
                .retryAfterSeconds(5)
                .policyId(policy.getPolicyId())
                .algorithm("REDIS_UNAVAILABLE_FAIL_CLOSED")
                .redisUnavailable(true)
                .build();
    }

    private String nullToUnknown(String value) {
        return Objects.requireNonNullElse(value, "unknown");
    }
}
