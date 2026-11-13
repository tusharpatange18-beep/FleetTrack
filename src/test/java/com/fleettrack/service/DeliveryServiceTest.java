package com.fleettrack.service;

import com.fleettrack.dto.request.DeliveryStatusUpdateRequestDto;
import com.fleettrack.entity.Delivery;
import com.fleettrack.entity.Driver;
import com.fleettrack.entity.Vehicle;
import com.fleettrack.entity.enums.DeliveryStatus;
import com.fleettrack.entity.enums.DriverStatus;
import com.fleettrack.entity.enums.VehicleStatus;
import com.fleettrack.exception.BadRequestException;
import com.fleettrack.exception.ConflictException;
import com.fleettrack.exception.ResourceNotFoundException;
import com.fleettrack.mapper.DeliveryMapper;
import com.fleettrack.repository.CustomerRepository;
import com.fleettrack.repository.DeliveryRepository;
import com.fleettrack.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static com.fleettrack.entity.enums.DeliveryStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class DeliveryServiceTest {

    @Mock DeliveryRepository deliveryRepository;
    @Mock CustomerRepository customerRepository;
    @Mock VehicleRepository vehicleRepository;
    @Mock DeliveryMapper deliveryMapper;
    @InjectMocks DeliveryService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ---- state machine, checked exhaustively ----

    private static final Set<String> ALLOWED = Set.of(
            "CREATED->ASSIGNED", "CREATED->CANCELLED",
            "ASSIGNED->PICKED_UP", "ASSIGNED->CANCELLED",
            "PICKED_UP->IN_TRANSIT",
            "IN_TRANSIT->DELIVERED");

    static Stream<Arguments> allPairs() {
        return Arrays.stream(DeliveryStatus.values()).flatMap(from ->
                Arrays.stream(DeliveryStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairs")
    void transitionTable_allowsExactlyTheDocumentedEdges(DeliveryStatus from, DeliveryStatus to) {
        assertEquals(ALLOWED.contains(from + "->" + to), DeliveryService.isValidTransition(from, to));
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryStatus.class, names = {"DELIVERED", "CANCELLED"})
    void terminalStates_haveNoExits(DeliveryStatus terminal) {
        for (DeliveryStatus to : DeliveryStatus.values()) {
            assertFalse(DeliveryService.isValidTransition(terminal, to));
        }
    }

    // ---- updateStatus ----

    @Test
    void updateStatus_toAssigned_isRejectedBeforeTouchingTheDatabase() {
        assertThrows(BadRequestException.class, () -> service.updateStatus(1L, statusRequest(ASSIGNED)));
        verifyNoInteractions(deliveryRepository);
    }

    @Test
    void updateStatus_invalidJump_isConflict() {
        when(deliveryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(delivery(CREATED, null)));
        assertThrows(ConflictException.class, () -> service.updateStatus(1L, statusRequest(DELIVERED)));
    }

    @Test
    void updateStatus_delivered_releasesBusyVehicle() {
        Vehicle vehicle = vehicle(VehicleStatus.BUSY, activeDriver());
        Delivery delivery = delivery(IN_TRANSIT, vehicle);
        when(deliveryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(delivery));
        when(vehicleRepository.findByIdForUpdate(vehicle.getId())).thenReturn(Optional.of(vehicle));

        service.updateStatus(1L, statusRequest(DELIVERED));

        assertEquals(DELIVERED, delivery.getStatus());
        assertEquals(VehicleStatus.AVAILABLE, vehicle.getStatus());
    }

    @Test
    void updateStatus_nonTerminal_keepsVehicleBusy() {
        Vehicle vehicle = vehicle(VehicleStatus.BUSY, activeDriver());
        when(deliveryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(delivery(ASSIGNED, vehicle)));

        service.updateStatus(1L, statusRequest(PICKED_UP));

        assertEquals(VehicleStatus.BUSY, vehicle.getStatus());
        verify(vehicleRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void updateStatus_unknownDelivery_isNotFound() {
        when(deliveryRepository.findByIdForUpdate(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(9L, statusRequest(CANCELLED)));
    }

    // ---- assign ----

    @Test
    void assign_happyPath_marksDeliveryAssignedAndVehicleBusy() {
        Driver driver = activeDriver();
        Vehicle vehicle = vehicle(VehicleStatus.AVAILABLE, driver);
        Delivery delivery = delivery(CREATED, null);

        service.assign(delivery, vehicle);

        assertEquals(ASSIGNED, delivery.getStatus());
        assertSame(vehicle, delivery.getVehicle());
        assertSame(driver, delivery.getDriver());
        assertEquals(VehicleStatus.BUSY, vehicle.getStatus());
    }

    @Test
    void assign_rejectsDeliveryNotInCreated() {
        assertThrows(ConflictException.class,
                () -> service.assign(delivery(CANCELLED, null), vehicle(VehicleStatus.AVAILABLE, activeDriver())));
    }

    @ParameterizedTest
    @EnumSource(value = VehicleStatus.class, names = {"BUSY", "OFFLINE", "INACTIVE"})
    void assign_rejectsVehicleThatIsNotAvailable(VehicleStatus status) {
        assertThrows(ConflictException.class,
                () -> service.assign(delivery(CREATED, null), vehicle(status, activeDriver())));
    }

    @Test
    void assign_rejectsVehicleWithoutDriver() {
        assertThrows(ConflictException.class,
                () -> service.assign(delivery(CREATED, null), vehicle(VehicleStatus.AVAILABLE, null)));
    }

    @Test
    void assign_rejectsInactiveDriver() {
        Driver inactive = activeDriver();
        inactive.setStatus(DriverStatus.INACTIVE);
        assertThrows(ConflictException.class,
                () -> service.assign(delivery(CREATED, null), vehicle(VehicleStatus.AVAILABLE, inactive)));
    }

    @Test
    void assign_rejectsVehicleAlreadyOnAnActiveDelivery() {
        when(deliveryRepository.existsByVehicleIdAndStatusIn(any(), any())).thenReturn(true);
        Delivery delivery = delivery(CREATED, null);
        assertThrows(ConflictException.class,
                () -> service.assign(delivery, vehicle(VehicleStatus.AVAILABLE, activeDriver())));
        assertEquals(CREATED, delivery.getStatus(), "a failed assignment must not change state");
    }

    // ---- helpers ----

    private static DeliveryStatusUpdateRequestDto statusRequest(DeliveryStatus status) {
        DeliveryStatusUpdateRequestDto dto = new DeliveryStatusUpdateRequestDto();
        dto.setStatus(status);
        return dto;
    }

    private static Delivery delivery(DeliveryStatus status, Vehicle vehicle) {
        Delivery d = new Delivery();
        d.setId(1L);
        d.setStatus(status);
        d.setVehicle(vehicle);
        return d;
    }

    private static Vehicle vehicle(VehicleStatus status, Driver driver) {
        Vehicle v = new Vehicle();
        v.setId(7L);
        v.setStatus(status);
        v.setDriver(driver);
        return v;
    }

    private static Driver activeDriver() {
        Driver d = new Driver();
        d.setId(3L);
        d.setStatus(DriverStatus.ACTIVE);
        return d;
    }
}
