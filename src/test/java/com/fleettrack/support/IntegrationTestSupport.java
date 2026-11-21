package com.fleettrack.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the whole application (security, validation, Flyway schema on H2) and drives it over HTTP
 * with MockMvc. Every test starts from empty tables.
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        // Children before parents because of foreign keys
        for (String table : new String[]{"location_history", "delivery", "vehicle", "driver", "customer", "app_user"}) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    // ---- auth ----

    /** Registers the first user as ADMIN (bootstrap rule) and returns its token. */
    protected String adminToken() throws Exception {
        register("admin", "admin-password", "ADMIN", null);
        return login("admin", "admin-password");
    }

    protected String dispatcherToken(String adminToken) throws Exception {
        register("dispatcher", "dispatcher-password", "DISPATCHER", adminToken);
        return login("dispatcher", "dispatcher-password");
    }

    protected void register(String username, String password, String role, String token) throws Exception {
        MockHttpServletRequestBuilder req = post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", username, "password", password, "role", role)));
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        mvc.perform(req).andExpect(status().isCreated());
    }

    protected String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return read(result).get("token").asText();
    }

    // ---- HTTP helpers ----

    protected ResultActions get(String token, String url) throws Exception {
        return mvc.perform(withAuth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url), token));
    }

    protected ResultActions postJson(String token, String url, Object body) throws Exception {
        return mvc.perform(withAuth(post(url), token).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    protected ResultActions patchJson(String token, String url, Object body) throws Exception {
        return mvc.perform(withAuth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(url), token).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    protected ResultActions patch(String token, String url) throws Exception {
        return mvc.perform(withAuth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(url), token));
    }

    protected ResultActions delete(String token, String url) throws Exception {
        return mvc.perform(withAuth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url), token));
    }

    private MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder req, String token) {
        return token == null ? req : req.header("Authorization", "Bearer " + token);
    }

    protected String json(Object body) throws Exception {
        return body instanceof String s ? s : objectMapper.writeValueAsString(body);
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected long idOf(ResultActions actions) throws Exception {
        return read(actions.andReturn()).get("id").asLong();
    }

    // ---- fixtures ----

    protected long createDriver(String token, String phone, String license) throws Exception {
        return idOf(postJson(token, "/api/drivers", Map.of(
                "name", "Driver " + phone, "phone", phone, "licenseNumber", license, "status", "ACTIVE"))
                .andExpect(status().isCreated()));
    }

    protected long createVehicle(String token, String registration) throws Exception {
        return idOf(postJson(token, "/api/vehicles", Map.of("registrationNumber", registration, "type", "VAN"))
                .andExpect(status().isCreated()));
    }

    protected void moveVehicle(String token, long vehicleId, double lat, double lon) throws Exception {
        patchJson(token, "/api/vehicles/" + vehicleId + "/location", Map.of("latitude", lat, "longitude", lon))
                .andExpect(status().isOk());
    }

    /** An AVAILABLE vehicle with an ACTIVE driver at the given position. */
    protected long readyVehicle(String token, String registration, double lat, double lon) throws Exception {
        long vehicleId = createVehicle(token, registration);
        long driverId = createDriver(token, "9" + Math.abs(registration.hashCode()), "LIC-" + registration);
        patch(token, "/api/drivers/" + driverId + "/assign-vehicle/" + vehicleId).andExpect(status().isOk());
        moveVehicle(token, vehicleId, lat, lon);
        return vehicleId;
    }

    protected long createCustomer(String token, double pickupLat, double pickupLon) throws Exception {
        return idOf(postJson(token, "/api/customers", Map.of(
                "name", "Customer", "phone", "9000000000",
                "pickupLatitude", pickupLat, "pickupLongitude", pickupLon,
                "dropLatitude", pickupLat + 0.05, "dropLongitude", pickupLon + 0.05))
                .andExpect(status().isCreated()));
    }

    protected long createDelivery(String token, long customerId) throws Exception {
        return idOf(postJson(token, "/api/deliveries", Map.of("customerId", customerId))
                .andExpect(status().isCreated()));
    }
}
