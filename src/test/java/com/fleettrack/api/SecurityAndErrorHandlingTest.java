package com.fleettrack.api;

import com.fleettrack.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every failure must come back as the right status code with the ApiError JSON body. */
class SecurityAndErrorHandlingTest extends IntegrationTestSupport {

    // ---- 401 / 403 ----

    @Test
    void requestWithoutToken_is401WithJsonBody() throws Exception {
        get(null, "/api/vehicles")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/vehicles"));
    }

    @Test
    void requestWithGarbageToken_is401() throws Exception {
        get("not-a-jwt", "/api/vehicles").andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPassword_is401() throws Exception {
        adminToken();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "admin", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void dispatcherCannotCreateDrivers_butCanReadThem() throws Exception {
        String dispatcher = dispatcherToken(adminToken());

        postJson(dispatcher, "/api/drivers", Map.of(
                "name", "D", "phone", "1", "licenseNumber", "L", "status", "ACTIVE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        get(dispatcher, "/api/drivers").andExpect(status().isOk());
    }

    @Test
    void anonymousCannotRegisterAdminOnceAUserExists() throws Exception {
        adminToken();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "eve", "password", "password-123", "role", "ADMIN"))))
                .andExpect(status().isForbidden());
    }

    // ---- 400 ----

    @Test
    void missingRequiredField_is400WithFieldName() throws Exception {
        postJson(adminToken(), "/api/vehicles", Map.of("registrationNumber", "MH01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("type: must not be null"));
    }

    @Test
    void outOfRangeQueryParam_is400NotServerError() throws Exception {
        String token = adminToken();
        get(token, "/api/vehicles/nearby?latitude=100&longitude=72").andExpect(status().isBadRequest());
        get(token, "/api/vehicles/nearby?latitude=19&longitude=72&radiusKm=-5").andExpect(status().isBadRequest());
        get(token, "/api/vehicles/nearby?latitude=19&longitude=72&radiusKm=10000").andExpect(status().isBadRequest());
    }

    @Test
    void missingAndMistypedParams_are400() throws Exception {
        String token = adminToken();
        get(token, "/api/vehicles/nearby?longitude=72").andExpect(status().isBadRequest());
        get(token, "/api/vehicles/nearby?latitude=abc&longitude=72").andExpect(status().isBadRequest());
        get(token, "/api/vehicles/not-a-number").andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonAndUnknownEnum_are400() throws Exception {
        String token = adminToken();
        postJson(token, "/api/vehicles", "{not json").andExpect(status().isBadRequest());
        postJson(token, "/api/vehicles", Map.of("registrationNumber", "MH01", "type", "SPACESHIP"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shortPassword_isRejected() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "shorty", "password", "123"))))
                .andExpect(status().isBadRequest());
    }

    // ---- 404 / 405 / 409 ----

    @Test
    void unknownIdAndUnknownRoute_are404() throws Exception {
        String token = adminToken();
        get(token, "/api/vehicles/999999").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Vehicle not found with id: 999999"));
        get(token, "/api/no-such-endpoint").andExpect(status().isNotFound());
    }

    @Test
    void wrongHttpMethod_is405() throws Exception {
        String token = adminToken();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/vehicles").header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void duplicates_are409() throws Exception {
        String token = adminToken();
        createVehicle(token, "MH01AB1234");
        postJson(token, "/api/vehicles", Map.of("registrationNumber", "MH01AB1234", "type", "VAN"))
                .andExpect(status().isConflict());

        createDriver(token, "9876543210", "LIC-1");
        postJson(token, "/api/drivers", Map.of(
                "name", "Other", "phone", "9876543210", "licenseNumber", "LIC-2", "status", "ACTIVE"))
                .andExpect(status().isConflict());
    }
}
