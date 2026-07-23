package com.rlaas.ratelimiter.Controller;

import com.rlaas.ratelimiter.Service.ProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@CrossOrigin("*")
@RestController
@RequestMapping("/r")
@RequiredArgsConstructor
public class ProxyController {

    private final ProxyService proxyService;

    // No @RequestBody here on purpose: binding the body to byte[]/DataBuffer via
    // @RequestBody fully reads it into memory before this method even runs, which
    // defeats streaming. request.getBody() is the raw, un-buffered Flux<DataBuffer>
    // straight off the connection.
    @RequestMapping(
            value = "/{routeId}",
            method = {
                    RequestMethod.GET,
                    RequestMethod.POST,
                    RequestMethod.PUT,
                    RequestMethod.DELETE,
                    RequestMethod.PATCH,
                    RequestMethod.OPTIONS,
                    RequestMethod.HEAD
            }
    )
    public Mono<ResponseEntity<Flux<DataBuffer>>> proxyRequest(
            @PathVariable UUID routeId,
            @RequestHeader(required = false) MultiValueMap<String, String> headers,
            @RequestParam(required = false) MultiValueMap<String, String> queryParams,
            ServerHttpRequest request) {

        return proxyService.forward(
                routeId,
                request.getMethod(),
                request.getBody(),
                headers,
                queryParams,
                request
        );
    }
}
