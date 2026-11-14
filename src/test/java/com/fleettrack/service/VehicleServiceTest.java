package com.fleettrack.service;

import com.fleettrack.dto.request.VehicleRequestDto;
import com.fleettrack.dto.request.VehicleUpdateRequestDto;
import com.fleettrack.entity.Driver;
import com.fleettrack.entity.Vehicle;
import com.fleettrack.entity.enums.VehicleStatus;
import com.fleettrack.entity.enums.VehicleType;
import com.fleettrack.exception.BadRequestException;
import com.fleettrack.exception.ConflictException;
import com.fleettrack.exception.ResourceNotFoundException;
import com.fleettrack.mapper.VehicleMapper;
import com.fleettrack.repository.LocationHistoryRepository;
import com.fleettrack.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VehicleServiceTest {

    private VehicleRepository vehicleRepository;
    private VehicleService vehicleService;

    @BeforeEach
    void setUp() {
        vehicleRepository = mock(VehicleRepository.class);
        vehicleService = new VehicleService(vehicleRepository, mock(LocationHistoryRepository.class),
                new VehicleMapper(), mock(ProximitySearch.class), Clock.systemUTC());
    }

    @Test
    void createVehicle_duplicateRegistrationThrowsConflict() {
        when(vehicleRepository.existsByRegistrationNumber("MH01AB1234")).thenReturn(true);
        assertThrows(ConflictException.class, () -> vehicleService.createVehicle(createRequest("MH01AB1234", null)));
    }

    @Test
    void createVehicle_defaultsToAvailable() {
        when(vehicleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals("AVAILABLE", vehicleService.createVehicle(createRequest(" MH01AB1234 ", null)).getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = VehicleStatus.class, names = {"BUSY", "INACTIVE"})
    void createVehicle_rejectsSystemOwnedStatuses(VehicleStatus status) {
        assertThrows(BadRequestException.class, () -> vehicleService.createVehicle(createRequest("MH01", status)));
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void getVehicle_notFoundThrowsResourceNotFoundException() {
        when(vehicleRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> vehicleService.getVehicle(999L));
    }

    @Test
    void updateVehicle_onlyChangesFieldsThatAreSent() {
        Vehicle vehicle = vehicle(VehicleStatus.AVAILABLE);
        when(vehicleRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vehicle));

        VehicleUpdateRequestDto request = new VehicleUpdateRequestDto();
        request.setType(VehicleType.TRUCK);
        vehicleService.updateVehicle(1L, request);

        assertEquals(VehicleType.TRUCK, vehicle.getType());
        assertEquals("REG-1", vehicle.getRegistrationNumber());
        assertEquals(VehicleStatus.AVAILABLE, vehicle.getStatus());
    }

    @Test
    void updateVehicle_cannotChangeStatusWhileBusy() {
        when(vehicleRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vehicle(VehicleStatus.BUSY)));
        VehicleUpdateRequestDto request = new VehicleUpdateRequestDto();
        request.setStatus(VehicleStatus.OFFLINE);
        assertThrows(ConflictException.class, () -> vehicleService.updateVehicle(1L, request));
    }

    @Test
    void updateVehicle_blankRegistrationIsRejected() {
        when(vehicleRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vehicle(VehicleStatus.AVAILABLE)));
        VehicleUpdateRequestDto request = new VehicleUpdateRequestDto();
        request.setRegistrationNumber("   ");
        assertThrows(BadRequestException.class, () -> vehicleService.updateVehicle(1L, request));
    }

    @Test
    void deleteVehicle_deactivatesAndReleasesDriver() {
        Vehicle vehicle = vehicle(VehicleStatus.AVAILABLE);
        vehicle.setDriver(new Driver());
        when(vehicleRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vehicle));

        vehicleService.deleteVehicle(1L);

        assertEquals(VehicleStatus.INACTIVE, vehicle.getStatus());
        assertNull(vehicle.getDriver());
    }

    @Test
    void deleteVehicle_refusedDuringDelivery() {
        when(vehicleRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vehicle(VehicleStatus.BUSY)));
        assertThrows(ConflictException.class, () -> vehicleService.deleteVehicle(1L));
    }

    private static VehicleRequestDto createRequest(String registration, VehicleStatus status) {
        VehicleRequestDto request = new VehicleRequestDto();
        request.setRegistrationNumber(registration);
        request.setType(VehicleType.VAN);
        request.setStatus(status);
        return request;
    }

    private static Vehicle vehicle(VehicleStatus status) {
        Vehicle v = new Vehicle();
        v.setId(1L);
        v.setRegistrationNumber("REG-1");
        v.setType(VehicleType.VAN);
        v.setStatus(status);
        return v;
    }
}
