package com.fleettrack.api;

import com.fleettrack.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Health probe and CORS behaviour seen by load balancers and browsers. */
class OperationsTest extends IntegrationTestSupport {

    @Test
    void healthIsPublicAndUp() throws Exception {
        get(null, "/actuator/health").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        get(null, "/actuator/health/liveness").andExpect(status().isOk());
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        get(adminToken(), "/actuator/env").andExpect(status().isNotFound());
    }

    @Test
    void corsPreflight_allowsConfiguredOriginOnly() throws Exception {
        mvc.perform(options("/api/vehicles")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        mvc.perform(options("/api/vehicles")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
