package com.fleettrack.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fleettrack.config.FleetProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimitFilterTest {

    /** A clock the test can move forward. */
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-14T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private MutableClock clock;
    private LoginRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        FleetProperties properties = new FleetProperties(Duration.ofMinutes(15), 25, List.of(), 3);
        filter = new LoginRateLimitFilter(properties, new ObjectMapper().registerModule(new JavaTimeModule()), clock);
    }

    @Test
    void allowsTheConfiguredBurstThenAnswers429WithRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertEquals(200, login("10.0.0.1").getStatus());
        }
        MockHttpServletResponse blocked = login("10.0.0.1");
        assertEquals(429, blocked.getStatus());
        assertEquals("20", blocked.getHeader("Retry-After")); // 3 per minute = one token every 20 s
        assertTrue(blocked.getContentAsString().contains("Too many login attempts"));
    }

    @Test
    void tokensRefillOverTime() throws Exception {
        for (int i = 0; i < 3; i++) login("10.0.0.1");
        assertEquals(429, login("10.0.0.1").getStatus());

        clock.now = clock.now.plusSeconds(20);
        assertEquals(200, login("10.0.0.1").getStatus());
        assertEquals(429, login("10.0.0.1").getStatus());
    }

    @Test
    void eachIpHasItsOwnBucket() throws Exception {
        for (int i = 0; i < 3; i++) login("10.0.0.1");
        assertEquals(429, login("10.0.0.1").getStatus());
        assertEquals(200, login("10.0.0.2").getStatus());
    }

    @Test
    void otherEndpointsAreNotLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
            request.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }
    }

    private MockHttpServletResponse login(String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
