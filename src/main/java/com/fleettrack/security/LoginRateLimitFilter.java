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
        // Bucket size = attempts allowed per minute (at least 1)
        this.capacity = Math.max(1, properties.loginAttemptsPerMinute());
        // A full bucket refills over one minute (60,000 ms), so this many tokens come back per ms
        this.refillPerMilli = capacity / 60_000.0;
    }

    /** This filter only cares about login requests; every other request skips it. */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        boolean isLoginRequest = "POST".equals(request.getMethod()) && LOGIN_PATH.equals(request.getRequestURI());
        return !isLoginRequest;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (buckets.size() > MAX_TRACKED_CLIENTS) {
            buckets.clear(); // crude but safe: everyone simply gets a fresh bucket
        }

        // getRemoteAddr honours X-Forwarded-For only when server.forward-headers-strategy trusts the proxy
        String clientIp = request.getRemoteAddr();
        // computeIfAbsent: get this IP's bucket, or create a full one if it is the IP's first attempt.
        // It does both steps as one safe operation, so two requests at once cannot create two buckets.
        Bucket bucket = buckets.computeIfAbsent(clientIp, ip -> new Bucket(capacity, clock.millis()));

        long waitMillis = bucket.tryConsume(clock.millis());
        if (waitMillis > 0) {
            // Bucket is empty: answer 429 Too Many Requests and say how long to wait
            long waitSeconds = (long) Math.ceil(waitMillis / 1000.0); // round up, e.g. 1500 ms -> 2 s
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(waitSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiError.of(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many login attempts; try again later", request.getRequestURI()));
            return; // stop here: the login is not attempted
        }
        // A token was available: let the login request continue
        chain.doFilter(request, response);
    }

    /** Package-private for tests. */
    void reset() {
        buckets.clear();
    }

    /** One IP's token bucket. Each login attempt takes one token; tokens slowly come back. */
    private final class Bucket {
        private double tokens;     // tokens left (can be a fraction while refilling)
        private long lastRefill;   // time of the last refill, in ms

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.lastRefill = now;
        }

        /**
         * Takes one token; returns 0 on success, otherwise milliseconds until a token is available.
         * synchronized: only one thread at a time may change this bucket.
         */
        synchronized long tryConsume(long now) {
            // 1) Add the tokens earned since the last refill, but never more than the bucket size
            long elapsedMillis = now - lastRefill;
            double refilled = tokens + elapsedMillis * refillPerMilli;
            tokens = Math.min(capacity, refilled);
            lastRefill = now;

            // 2) Enough for one attempt? Take it
            if (tokens >= 1) {
                tokens = tokens - 1;
                return 0;
            }

            // 3) Not enough: work out how long until the bucket reaches one whole token
            double missing = 1 - tokens;
            return (long) Math.ceil(missing / refillPerMilli);
        }
    }
}
