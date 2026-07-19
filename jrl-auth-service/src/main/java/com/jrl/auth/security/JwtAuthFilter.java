package com.jrl.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Reads "Authorization: Bearer {token}", validates it, and — if valid —
 * puts an AuthenticatedUser into the SecurityContext for the rest of the
 * request. No token, or an invalid one, just means the request proceeds
 * unauthenticated; SecurityConfig is what actually decides which endpoints
 * require authentication.
 */
@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parseAndValidate(token);

                AuthenticatedUser principal = new AuthenticatedUser(
                        UUID.fromString(claims.getSubject()),
                        claims.get("email", String.class),
                        UUID.fromString(claims.get("customerId", String.class))
                );

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (JwtException | IllegalArgumentException e) {
                // Invalid/expired token: leave the context unauthenticated rather than
                // failing the request here — SecurityConfig's authorization rules are
                // what turn "unauthenticated" into a 401 for endpoints that need it.
                log.debug("Rejected JWT: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
