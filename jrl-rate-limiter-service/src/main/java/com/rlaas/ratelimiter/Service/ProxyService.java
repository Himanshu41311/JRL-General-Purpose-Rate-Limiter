package com.rlaas.ratelimiter.Service;

import com.rlaas.ratelimiter.Model.CachedPolicy;
import com.rlaas.ratelimiter.Model.CachedRoute;
import com.rlaas.ratelimiter.Model.RateLimitResult;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class ProxyService {

    // Hop-by-hop / connection-specific headers must never be forwarded (RFC 7230 6.1).
    // Applied to both directions: stripped from the outgoing request, and stripped
    // again from the backend's response before we re-wrap it in our own.
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length"
    );

    private static final Set<HttpMethod> METHODS_WITH_BODY = Set.of(
            HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH
    );

    private final RouteLookupService routeLookupService;
    private final PolicyLookupService policyLookupService;
    private final RateLimitService rateLimitService;
    private final WebClient proxyWebClient;
    private final MeterRegistry meterRegistry;
    private final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    public ProxyService(RouteLookupService routeLookupService,
                         PolicyLookupService policyLookupService,
                         RateLimitService rateLimitService,
                         WebClient proxyWebClient,
                         MeterRegistry meterRegistry) {
        this.routeLookupService = routeLookupService;
        this.policyLookupService = policyLookupService;
        this.rateLimitService = rateLimitService;
        this.proxyWebClient = proxyWebClient;
        this.meterRegistry = meterRegistry;
    }

    public Mono<ResponseEntity<Flux<DataBuffer>>> forward(
            UUID routeId,
            HttpMethod method,
            Flux<DataBuffer> body,
            MultiValueMap<String, String> headers,
            MultiValueMap<String, String> queryParams,
            ServerHttpRequest request
    ) {
        return routeLookupService.findActiveRoute(routeId)
                .flatMap(route -> policyLookupService.findPoliciesForRoute(routeId)
                        .flatMap(policies -> handlePolicies(route, policies, method, body, headers, queryParams, request)))
                .switchIfEmpty(Mono.defer(() -> {
                    recordOutcome(null, "not_found");
                    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Flux.<DataBuffer>empty()));
                }));
    }

    private Mono<ResponseEntity<Flux<DataBuffer>>> handlePolicies(
            CachedRoute route,
            List<CachedPolicy> policies,
            HttpMethod method,
            Flux<DataBuffer> body,
            MultiValueMap<String, String> headers,
            MultiValueMap<String, String> queryParams,
            ServerHttpRequest request
    ) {
        if (policies.isEmpty()) {
            recordOutcome(route, "no_policies");
            return Mono.just(
                    ResponseEntity.status(HttpStatus.FORBIDDEN).body(textBody("Irrelevant Request"))
            );
        }

        return rateLimitService.evaluate(route, policies, request)
                .flatMap(result -> {
                    if (!result.isAllowed()) {
                        recordOutcome(route, result.isRedisUnavailable() ? "denied_fail_closed" : "denied");
                        return Mono.just(tooManyRequests(result));
                    }
                    recordOutcome(route, result.isRedisUnavailable() ? "allowed_fail_open" : "allowed");
                    return forwardToBackend(route, method, body, headers, queryParams)
                            .map(response -> withRateLimitHeaders(response, result));
                });
    }

    private Mono<ResponseEntity<Flux<DataBuffer>>> forwardToBackend(
            CachedRoute route,
            HttpMethod method,
            Flux<DataBuffer> body,
            MultiValueMap<String, String> headers,
            MultiValueMap<String, String> queryParams
    ) {
        URI targetUri = UriComponentsBuilder
                .fromHttpUrl(route.getTargetUrl())
                .queryParams(queryParams != null ? queryParams : new LinkedMultiValueMap<>())
                .build()
                .encode()
                .toUri();

        WebClient.RequestBodySpec requestSpec = proxyWebClient
                .method(method)
                .uri(targetUri)
                .headers(httpHeaders -> copyForwardableHeaders(headers, httpHeaders));

        WebClient.RequestHeadersSpec<?> readySpec;
        if (METHODS_WITH_BODY.contains(method)) {
            readySpec = requestSpec.body(BodyInserters.fromDataBuffers(body));
        } else {
            readySpec = requestSpec;
        }

        return readySpec
                .exchangeToMono(clientResponse -> {
                    HttpHeaders responseHeaders = new HttpHeaders();
                    copyForwardableHeaders(clientResponse.headers().asHttpHeaders(), responseHeaders);

                    return Mono.just(
                            ResponseEntity.status(clientResponse.statusCode())
                                    .headers(responseHeaders)
                                    .body(clientResponse.bodyToFlux(DataBuffer.class))
                    );
                })
                .onErrorResume(ex -> {
                    log.error("Error forwarding request to {}: {}", route.getTargetUrl(), ex.getMessage());
                    meterRegistry.counter("rate_limiter_upstream_errors_total",
                                    "customerId", nullToUnknown(route.getCustomerId()))
                            .increment();
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Flux.<DataBuffer>empty()));
                });
    }

    private void copyForwardableHeaders(MultiValueMap<String, String> source, HttpHeaders target) {
        if (source == null) {
            return;
        }
        source.forEach((name, values) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                target.addAll(name, values);
            }
        });
    }

    private ResponseEntity<Flux<DataBuffer>> tooManyRequests(RateLimitResult result) {
        HttpHeaders headers = rateLimitHeaders(result);
        headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(result.getRetryAfterSeconds()));
        if (result.isRedisUnavailable()) {
            headers.add("X-RateLimit-Status", "redis-unavailable");
        }
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body(textBody("Rate limit exceeded"));
    }

    private ResponseEntity<Flux<DataBuffer>> withRateLimitHeaders(ResponseEntity<Flux<DataBuffer>> response, RateLimitResult result) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(response.getHeaders());
        headers.putAll(rateLimitHeaders(result));
        if (result.isRedisUnavailable()) {
            headers.add("X-RateLimit-Status", "redis-unavailable");
        }
        return ResponseEntity.status(response.getStatusCode())
                .headers(headers)
                .body(response.getBody());
    }

    private HttpHeaders rateLimitHeaders(RateLimitResult result) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-RateLimit-Limit", String.valueOf(result.getLimit()));
        headers.add("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
        headers.add("X-RateLimit-Reset", String.valueOf(result.getResetAfterSeconds()));
        return headers;
    }

    private Flux<DataBuffer> textBody(String text) {
        return Flux.just(bufferFactory.wrap(text.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Tagged by customerId, not routeId: a customer might register hundreds of
     * routes, and routeId is a UUID, so tagging per-route risks unbounded metric
     * cardinality in Prometheus. If you need per-route breakdowns later, prefer a
     * separate low-cardinality "route name/label" the admin service assigns,
     * rather than the raw UUID.
     */
    private void recordOutcome(CachedRoute route, String outcome) {
        String customerId = route != null ? nullToUnknown(route.getCustomerId()) : "unknown";
        meterRegistry.counter("rate_limiter_requests_total",
                        "customerId", customerId,
                        "outcome", outcome)
                .increment();
    }

    private String nullToUnknown(String value) {
        return Objects.requireNonNullElse(value, "unknown");
    }
}
