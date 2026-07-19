package com.rlaas.ratelimiter.Service;

import org.springframework.http.server.reactive.ServerHttpRequest;

public class IpResolver {

    public static String resolve(ServerHttpRequest request) {

        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");

        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }

        String realIp = request.getHeaders().getFirst("X-Real-IP");

        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }

        if (request.getRemoteAddress() != null &&
                request.getRemoteAddress().getAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }

        return "UNKNOWN";
    }
}

