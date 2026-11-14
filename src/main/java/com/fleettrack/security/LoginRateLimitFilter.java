package com.fleettrack.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleettrack.config.FleetProperties;
import com.fleettrack.exception.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing: each client IP gets a token bucket of
 * {@code fleettrack.login-attempts-per-minute} login attempts that refills continuously.
 * An empty bucket answers 429 with Retry-After.
 * <p>
 * In-memory and per instance, which is enough for a single node; several instances behind a
 * load balancer would need a shared store (e.g. Redis) instead.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";
    /** Upper bound on tracked IPs so a flood of spoofed addresses cannot exhaust memory. */
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final double capacity;
    private final double refillPerMilli;

    public LoginRateLimitFilter(FleetProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.capacity = Math.max(1, properties.loginAttemptsPerMinute());
        this.refillPerMilli = capacity / 60_000.0;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && LOGIN_PATH.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (buckets.size() > MAX_TRACKED_CLIENTS) {
            buckets.clear(); // crude but safe: everyone simply gets a fresh bucket
        }
        // getRemoteAddr honours X-Forwarded-For only when server.forward-headers-strategy trusts the proxy
        Bucket bucket = buckets.computeIfAbsent(request.getRemoteAddr(), ip -> new Bucket(capacity, clock.millis()));

        long waitMillis = bucket.tryConsume(clock.millis());
        if (waitMillis > 0) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf((waitMillis + 999) / 1000));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiError.of(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many login attempts; try again later", request.getRequestURI()));
            return;
        }
        chain.doFilter(request, response);
    }

    /** Package-private for tests. */
    void reset() {
        buckets.clear();
    }

    private final class Bucket {
        private double tokens;
        private long lastRefill;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.lastRefill = now;
        }

        /** Takes one token; returns 0 on success, otherwise milliseconds until a token is available. */
        synchronized long tryConsume(long now) {
            tokens = Math.min(capacity, tokens + (now - lastRefill) * refillPerMilli);
            lastRefill = now;
            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }
            return (long) Math.ceil((1 - tokens) / refillPerMilli);
        }
    }
}
