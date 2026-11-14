package com.fleettrack.api;

import com.fleettrack.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end business flows over HTTP against the Flyway schema. */
class FleetWorkflowTest extends IntegrationTestSupport {

    private String token;

    @BeforeEach
    void login() throws Exception {
        token = adminToken();
    }

    @Test
    void fullDeliveryLifecycle_releasesVehicleAtTheEnd() throws Exception {
        long vehicleId = readyVehicle(token, "MH01AA0001", 19.07, 72.87);
        long deliveryId = createDelivery(token, createCustomer(token, 19.07, 72.87));

        patch(token, "/api/deliveries/" + deliveryId + "/assign?vehicleId=" + vehicleId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.vehicleId").value(vehicleId));
        get(token, "/api/vehicles/" + vehicleId).andExpect(jsonPath("$.status").value("BUSY"));

        for (String next : new String[]{"PICKED_UP", "IN_TRANSIT", "DELIVERED"}) {
            patchJson(token, "/api/deliveries/" + deliveryId + "/status", Map.of("status", next))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(next));
        }
        get(token, "/api/vehicles/" + vehicleId).andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void cancellingAnAssignedDelivery_releasesVehicle() throws Exception {
        long vehicleId = readyVehicle(token, "MH01AA0002", 19.07, 72.87);
        long deliveryId = createDelivery(token, createCustomer(token, 19.07, 72.87));
        patch(token, "/api/deliveries/" + deliveryId + "/assign?vehicleId=" + vehicleId).andExpect(status().isOk());

        patchJson(token, "/api/deliveries/" + deliveryId + "/status", Map.of("status", "CANCELLED"))
                .andExpect(status().isOk());
        get(token, "/api/vehicles/" + vehicleId).andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void statusEndpoint_refusesAssignedAndInvalidJumps() throws Exception {
        long deliveryId = createDelivery(token, createCustomer(token, 19.07, 72.87));

        // ASSIGNED needs a vehicle, so only /assign may set it
        patchJson(token, "/api/deliveries/" + deliveryId + "/status", Map.of("status", "ASSIGNED"))
                .andExpect(status().isBadRequest());
        // CREATED -> DELIVERED skips the state machine
        patchJson(token, "/api/deliveries/" + deliveryId + "/status", Map.of("status", "DELIVERED"))
                .andExpect(status().isConflict());
    }

    @Test
    void vehicleOnDelivery_cannotBeDeletedOrStatusEditedOrReassigned() throws Exception {
        long vehicleId = readyVehicle(token, "MH01AA0003", 19.07, 72.87);
        long customerId = createCustomer(token, 19.07, 72.87);
        long first = createDelivery(token, customerId);
        long second = createDelivery(token, customerId);
        patch(token, "/api/deliveries/" + first + "/assign?vehicleId=" + vehicleId).andExpect(status().isOk());

        delete(token, "/api/vehicles/" + vehicleId).andExpect(status().isConflict());
        patchJson(token, "/api/vehicles/" + vehicleId, Map.of("status", "OFFLINE")).andExpect(status().isConflict());
        patch(token, "/api/deliveries/" + second + "/assign?vehicleId=" + vehicleId).andExpect(status().isConflict());
    }

    @Test
    void vehicleLeftBusyWithoutADelivery_canBeRepairedThroughTheApi() throws Exception {
        long vehicleId = readyVehicle(token, "MH01STUCK1", 19.07, 72.87);
        // Inconsistent row as older versions could leave it: BUSY, no delivery holds it
        jdbc.update("UPDATE vehicle SET status = 'BUSY' WHERE id = ?", vehicleId);

        patchJson(token, "/api/vehicles/" + vehicleId, Map.of("status", "AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void usersCannotSetBusyByHand_butPartialUpdateWorks() throws Exception {
        long vehicleId = createVehicle(token, "MH01AA0004");

        patchJson(token, "/api/vehicles/" + vehicleId, Map.of("status", "BUSY")).andExpect(status().isBadRequest());
        patchJson(token, "/api/vehicles/" + vehicleId, Map.of("type", "TRUCK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TRUCK"))
                .andExpect(jsonPath("$.registrationNumber").value("MH01AA0004"));
    }

    @Test
    void vehicleWithoutDriverOrWithInactiveDriver_cannotBeAssigned() throws Exception {
        long deliveryId = createDelivery(token, createCustomer(token, 19.07, 72.87));

        long noDriver = createVehicle(token, "MH01AA0005");
        patch(token, "/api/deliveries/" + deliveryId + "/assign?vehicleId=" + noDriver).andExpect(status().isConflict());

        long vehicleId = readyVehicle(token, "MH01AA0006", 19.07, 72.87);
        long driverId = read(get(token, "/api/vehicles/" + vehicleId).andReturn()).get("driverId").asLong();
        delete(token, "/api/drivers/" + driverId).andExpect(status().isNoContent());
        // Deactivating the driver also unlinks them from the vehicle
        get(token, "/api/vehicles/" + vehicleId).andExpect(jsonPath("$.driverId").doesNotExist());
        patch(token, "/api/deliveries/" + deliveryId + "/assign?vehicleId=" + vehicleId).andExpect(status().isConflict());
    }

    @Test
    void driverCanBeUnassignedOnlyWhenVehicleIsFree() throws Exception {
        long vehicleId = readyVehicle(token, "MH01AA0007", 19.07, 72.87);
        long driverId = read(get(token, "/api/vehicles/" + vehicleId).andReturn()).get("driverId").asLong();
        long deliveryId = createDelivery(token, createCustomer(token, 19.07, 72.87));
        patch(token, "/api/deliveries/" + deliveryId + "/assign?vehicleId=" + vehicleId).andExpect(status().isOk());

        delete(token, "/api/drivers/" + driverId + "/vehicle").andExpect(status().isConflict());

        patchJson(token, "/api/deliveries/" + deliveryId + "/status", Map.of("status", "CANCELLED")).andExpect(status().isOk());
        delete(token, "/api/drivers/" + driverId + "/vehicle").andExpect(status().isNoContent());
        get(token, "/api/vehicles/" + vehicleId).andExpect(jsonPath("$.driverId").doesNotExist());
    }

    @Test
    void locationUpdates_areRecordedAsHistoryNewestFirst() throws Exception {
        long vehicleId = createVehicle(token, "MH01AA0008");
        moveVehicle(token, vehicleId, 19.00, 72.80);
        moveVehicle(token, vehicleId, 19.01, 72.81);
        moveVehicle(token, vehicleId, 19.02, 72.82);

        get(token, "/api/vehicles/" + vehicleId + "/locations")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].latitude").value(19.02));
        get(token, "/api/vehicles/" + vehicleId).andExpect(jsonPath("$.currentLatitude").value(19.02));

        get(token, "/api/vehicles/" + vehicleId + "/locations?from=2030-01-01T00:00:00&to=2020-01-01T00:00:00")
                .andExpect(status().isBadRequest());
        get(token, "/api/vehicles/999999/locations").andExpect(status().isNotFound());
    }

    @Test
    void deactivatedVehicle_rejectsLocationUpdates() throws Exception {
        long vehicleId = createVehicle(token, "MH01AA0009");
        delete(token, "/api/vehicles/" + vehicleId).andExpect(status().isNoContent());
        patchJson(token, "/api/vehicles/" + vehicleId + "/location", Map.of("latitude", 19.0, "longitude", 72.0))
                .andExpect(status().isConflict());
    }

    @Test
    void nearbySearch_returnsOnlyAvailableVehiclesInRadiusNearestFirst() throws Exception {
        long near = readyVehicle(token, "MH01AA0010", 19.0760, 72.8777);   // search point
        long nearish = readyVehicle(token, "MH01AA0011", 19.1000, 72.8777); // about 2.7 km north
        readyVehicle(token, "DL01AA0001", 28.7041, 77.1025);                // Delhi, far away

        get(token, "/api/vehicles/nearby?latitude=19.0760&longitude=72.8777&radiusKm=10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].vehicleId").value(near))
                .andExpect(jsonPath("$[1].vehicleId").value(nearish));
    }
}
