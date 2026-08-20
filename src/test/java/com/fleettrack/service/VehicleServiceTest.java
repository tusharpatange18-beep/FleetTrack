package com.fleettrack.service;

import com.fleettrack.dto.response.NearbyVehicleResponseDto;
import com.fleettrack.entity.Vehicle;
import com.fleettrack.entity.enums.VehicleStatus;
import com.fleettrack.entity.enums.VehicleType;
import com.fleettrack.exception.ConflictException;
import com.fleettrack.mapper.VehicleMapper;
import com.fleettrack.repository.LocationHistoryRepository;
import com.fleettrack.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private LocationHistoryRepository locationHistoryRepository;

    @Mock
    private VehicleMapper vehicleMapper;

    @InjectMocks
    private VehicleService vehicleService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void nearbySearch_onlyReturnsAvailableVehicles() {
        Vehicle available = makeVehicle(1L, VehicleStatus.AVAILABLE, VehicleType.VAN, 19.0760, 72.8777);
        Vehicle busy = makeVehicle(2L, VehicleStatus.BUSY, VehicleType.BIKE, 19.0760, 72.8777);
        Vehicle offline = makeVehicle(3L, VehicleStatus.OFFLINE, VehicleType.TRUCK, 19.0760, 72.8777);

        when(vehicleRepository.findByStatus(VehicleStatus.AVAILABLE)).thenReturn(List.of(available));

        List<NearbyVehicleResponseDto> result = vehicleService.findNearbyVehicles(19.0760, 72.8777, 10.0);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getVehicleId());
        verify(vehicleRepository, never()).findAll(); // Must use filtered query, not findAll
    }

    @Test
    void nearbySearch_excludesVehiclesOutsideRadius() {
        // Vehicle in Mumbai
        Vehicle mumbai = makeVehicle(1L, VehicleStatus.AVAILABLE, VehicleType.VAN, 19.0760, 72.8777);
        // Vehicle in Delhi (~1400 km away)
        Vehicle delhi = makeVehicle(2L, VehicleStatus.AVAILABLE, VehicleType.TRUCK, 28.7041, 77.1025);

        when(vehicleRepository.findByStatus(VehicleStatus.AVAILABLE)).thenReturn(List.of(mumbai, delhi));

        // Search from Mumbai with 10 km radius
        List<NearbyVehicleResponseDto> result = vehicleService.findNearbyVehicles(19.0760, 72.8777, 10.0);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getVehicleId());
    }

    @Test
    void nearbySearch_sortsByDistanceAscending() {
        // Closer vehicle (~2 km away from search point)
        Vehicle closer = makeVehicle(1L, VehicleStatus.AVAILABLE, VehicleType.VAN, 19.0760, 72.8777);
        // Further vehicle (~5 km away from search point)
        Vehicle further = makeVehicle(2L, VehicleStatus.AVAILABLE, VehicleType.BIKE, 19.1100, 72.8777);

        when(vehicleRepository.findByStatus(VehicleStatus.AVAILABLE)).thenReturn(List.of(further, closer));

        List<NearbyVehicleResponseDto> result = vehicleService.findNearbyVehicles(19.0760, 72.8777, 20.0);

        assertEquals(2, result.size());
        assertTrue(result.get(0).getDistanceKm() <= result.get(1).getDistanceKm(),
                "Results should be sorted by distance ascending");
    }

    @Test
    void nearbySearch_excludesVehiclesWithNullLocation() {
        Vehicle noLocation = makeVehicle(1L, VehicleStatus.AVAILABLE, VehicleType.VAN, null, null);
        when(vehicleRepository.findByStatus(VehicleStatus.AVAILABLE)).thenReturn(List.of(noLocation));

        List<NearbyVehicleResponseDto> result = vehicleService.findNearbyVehicles(19.0760, 72.8777, 100.0);

        assertEquals(0, result.size());
    }

    @Test
    void createVehicle_duplicateRegistrationThrowsConflict() {
        when(vehicleRepository.existsByRegistrationNumber("MH01AB1234")).thenReturn(true);

        com.fleettrack.dto.request.VehicleRequestDto request = new com.fleettrack.dto.request.VehicleRequestDto();
        request.setRegistrationNumber("MH01AB1234");
        request.setType(VehicleType.VAN);
        request.setStatus(VehicleStatus.AVAILABLE);

        assertThrows(ConflictException.class, () -> vehicleService.createVehicle(request));
    }

    @Test
    void getVehicle_notFoundThrowsResourceNotFoundException() {
        when(vehicleRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(com.fleettrack.exception.ResourceNotFoundException.class, () -> vehicleService.getVehicle(999L));
    }

    // Helper
    private Vehicle makeVehicle(Long id, VehicleStatus status, VehicleType type, Double lat, Double lon) {
        Vehicle v = new Vehicle();
        v.setId(id);
        v.setStatus(status);
        v.setType(type);
        v.setCurrentLatitude(lat);
        v.setCurrentLongitude(lon);
        v.setRegistrationNumber("REG-" + id);
        return v;
    }
}
