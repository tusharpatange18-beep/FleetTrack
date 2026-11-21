package com.fleettrack.api;

import com.fleettrack.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Auto-assignment, per-delivery routes, stale-location filtering and the dashboard. */
class DispatchFeaturesTest extends IntegrationTestSupport {

    // Pickup point in central Pune
    private static final double LAT = 18.5204;
    private static final double LON = 73.8567;

    private String token;

    @BeforeEach
    void login() throws Exception {
        token = adminToken();
    }

    @Test
    void autoAssign_picksTheNearestVehicleThatCanTakeTheJob() throws Exception {
        long far = readyVehicle(token, "MH12FAR001", LAT + 0.09, LON);      // about 10 km
        long near = readyVehicle(token, "MH12NEAR01", LAT + 0.02, LON);     // about 2.2 km
        long noDriver = createVehicle(token, "MH12NODRV1");                  // closest, but no driver
        moveVehicle(token, noDriver, LAT, LON);
        long deliveryId = createDelivery(token, createCustomer(token, LAT, LON));

        postJson(token, "/api/deliveries/" + deliveryId + "/auto-assign", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.vehicleId").value(near))
                .andExpect(jsonPath("$.delivery.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.distanceKm").value(2.22));

        // The next delivery gets the next nearest, because `near` is now BUSY
        long second = createDelivery(token, createCustomer(token, LAT, LON));
        postJson(token, "/api/deliveries/" + second + "/auto-assign", Map.of())
                .andExpect(jsonPath("$.delivery.vehicleId").value(far));
    }

    @Test
    void autoAssign_ignoresVehiclesWithAStaleLocation() throws Exception {
        long stale = readyVehicle(token, "MH12STALE1", LAT, LON);
        long fresh = readyVehicle(token, "MH12FRESH1", LAT + 0.05, LON);
        jdbc.update("UPDATE vehicle SET location_updated_at = ? WHERE id = ?",
                LocalDateTime.now().minusHours(2), stale);
        long deliveryId = createDelivery(token, createCustomer(token, LAT, LON));

        postJson(token, "/api/deliveries/" + deliveryId + "/auto-assign", Map.of())
                .andExpect(jsonPath("$.delivery.vehicleId").value(fresh));
        get(token, "/api/vehicles/nearby?latitude=" + LAT + "&longitude=" + LON + "&radiusKm=20")
                .andExpect(jsonPath("$", hasSize(0))); // `fresh` is BUSY now, `stale` is hidden
    }

    @Test
    void autoAssign_withNobodyInRange_is409AndLeavesDeliveryCreated() throws Exception {
        readyVehicle(token, "DL01FAR001", 28.7041, 77.1025); // Delhi
        long deliveryId = createDelivery(token, createCustomer(token, LAT, LON));

        postJson(token, "/api/deliveries/" + deliveryId + "/auto-assign?radiusKm=50", Map.of())
                .andExpect(status().isConflict());
        get(token, "/api/deliveries/" + deliveryId).andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void autoAssign_respectsStateAndRadiusValidation() throws Exception {
        long vehicleId = readyVehicle(token, "MH12AA0001", LAT, LON);
        long deliveryId = createDelivery(token, createCustomer(token, LAT, LON));
        postJson(token, "/api/deliveries/" + deliveryId + "/auto-assign?radiusKm=0", Map.of())
                .andExpect(status().isBadRequest());

        patch(token, "/api/deliveries/" + deliveryId + "/assign?vehicleId=" + vehicleId).andExpect(status().isOk());
        postJson(token, "/api/deliveries/" + deliveryId + "/auto-assign", Map.of())
                .andExpect(status().isConflict());
    }

    @Test
    void delivery_routeDefaultsToCustomer_butCanBeOverridden() throws Exception {
        long customerId = createCustomer(token, LAT, LON);

        postJson(token, "/api/deliveries", Map.of("customerId", customerId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pickupLatitude").value(LAT))
                .andExpect(jsonPath("$.dropLatitude").value(LAT + 0.05));

        postJson(token, "/api/deliveries", Map.of("customerId", customerId,
                        "pickupLatitude", 18.60, "pickupLongitude", 73.80))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pickupLatitude").value(18.60))
                .andExpect(jsonPath("$.dropLatitude").value(LAT + 0.05)); // drop still from customer

        // Half a coordinate pair is a client error
        postJson(token, "/api/deliveries", Map.of("customerId", customerId, "pickupLatitude", 18.60))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dashboard_countsEveryStatusIncludingZeros() throws Exception {
        long vehicleId = readyVehicle(token, "MH12DASH01", LAT, LON);
        createVehicle(token, "MH12DASH02");
        long customerId = createCustomer(token, LAT, LON);
        long assigned = createDelivery(token, customerId);
        createDelivery(token, customerId);
        patch(token, "/api/deliveries/" + assigned + "/assign?vehicleId=" + vehicleId).andExpect(status().isOk());

        get(token, "/api/dashboard/summary")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehiclesByStatus.BUSY").value(1))
                .andExpect(jsonPath("$.vehiclesByStatus.AVAILABLE").value(1))
                .andExpect(jsonPath("$.vehiclesByStatus.OFFLINE").value(0))
                .andExpect(jsonPath("$.deliveriesByStatus.CREATED").value(1))
                .andExpect(jsonPath("$.deliveriesByStatus.ASSIGNED").value(1))
                .andExpect(jsonPath("$.deliveriesByStatus.DELIVERED").value(0))
                .andExpect(jsonPath("$.activeDeliveries").value(1))
                .andExpect(jsonPath("$.activeDrivers").value(1));
    }

    @Test
    void dashboard_requiresLogin() throws Exception {
        get(null, "/api/dashboard/summary").andExpect(status().isUnauthorized());
    }
}
