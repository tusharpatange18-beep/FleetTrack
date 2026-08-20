package com.fleettrack.service;

import com.fleettrack.dto.request.LocationUpdateRequestDto;
import com.fleettrack.dto.request.VehicleRequestDto;
import com.fleettrack.dto.response.NearbyVehicleResponseDto;
import com.fleettrack.dto.response.VehicleResponseDto;
import com.fleettrack.entity.LocationHistory;
import com.fleettrack.entity.Vehicle;
import com.fleettrack.entity.enums.VehicleStatus;
import com.fleettrack.exception.ConflictException;
import com.fleettrack.exception.ResourceNotFoundException;
import com.fleettrack.mapper.VehicleMapper;
import com.fleettrack.repository.LocationHistoryRepository;
import com.fleettrack.repository.VehicleRepository;
import com.fleettrack.util.HaversineCalculator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private static final Logger log = LoggerFactory.getLogger(VehicleService.class);

    private final VehicleRepository vehicleRepository;
    private final LocationHistoryRepository locationHistoryRepository;
    private final VehicleMapper vehicleMapper;

    public Page<VehicleResponseDto> getVehicles(Pageable pageable) {
        return vehicleRepository.findAll(pageable).map(vehicleMapper::toDto);
    }

    @Transactional
    public VehicleResponseDto createVehicle(VehicleRequestDto requestDto) {
        if (vehicleRepository.existsByRegistrationNumber(requestDto.getRegistrationNumber())) {
            throw new ConflictException("Vehicle with registration number already exists: " + requestDto.getRegistrationNumber());
        }
        Vehicle vehicle = vehicleMapper.toEntity(requestDto);
        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Created vehicle id={}, reg={}", saved.getId(), saved.getRegistrationNumber());
        return vehicleMapper.toDto(saved);
    }

    public VehicleResponseDto getVehicle(Long id) {
        return vehicleMapper.toDto(findVehicleById(id));
    }

    @Transactional
    public VehicleResponseDto updateVehicle(Long id, VehicleRequestDto requestDto) {
        Vehicle vehicle = findVehicleById(id);
        // Don't allow changing registration number to one that already exists
        if (!vehicle.getRegistrationNumber().equals(requestDto.getRegistrationNumber())
                && vehicleRepository.existsByRegistrationNumber(requestDto.getRegistrationNumber())) {
            throw new ConflictException("Registration number already in use: " + requestDto.getRegistrationNumber());
        }
        vehicle.setRegistrationNumber(requestDto.getRegistrationNumber());
        vehicle.setType(requestDto.getType());
        vehicle.setStatus(requestDto.getStatus());
        return vehicleMapper.toDto(vehicleRepository.save(vehicle));
    }

    /**
     * Updates the vehicle's current latitude/longitude and records a LocationHistory entry.
     * Both operations are atomic within a single transaction.
     */
    @Transactional
    public VehicleResponseDto updateLocation(Long id, LocationUpdateRequestDto requestDto) {
        Vehicle vehicle = findVehicleById(id);
        vehicle.setCurrentLatitude(requestDto.getLatitude());
        vehicle.setCurrentLongitude(requestDto.getLongitude());
        vehicle.setLocationUpdatedAt(LocalDateTime.now());

        // Save vehicle first so location_history FK is valid
        Vehicle saved = vehicleRepository.save(vehicle);

        LocationHistory history = new LocationHistory();
        history.setVehicle(saved);
        history.setLatitude(requestDto.getLatitude());
        history.setLongitude(requestDto.getLongitude());
        locationHistoryRepository.save(history);

        log.info("Updated location for vehicle id={}: lat={}, lon={}", id, requestDto.getLatitude(), requestDto.getLongitude());
        return vehicleMapper.toDto(saved);
    }

    /**
     * Soft-deletes a vehicle by setting its status to INACTIVE.
     * Historical data (deliveries, location history) is preserved.
     */
    @Transactional
    public void deleteVehicle(Long id) {
        Vehicle vehicle = findVehicleById(id);
        vehicle.setStatus(VehicleStatus.INACTIVE);
        vehicleRepository.save(vehicle);
        log.info("Soft-deleted vehicle id={}", id);
    }

    /**
     * Finds vehicles within a given radius using the Haversine formula.
     * Only vehicles with status=AVAILABLE and a known location are considered.
     * Results are sorted by distance ascending.
     */
    public List<NearbyVehicleResponseDto> findNearbyVehicles(double lat, double lon, double radiusKm) {
        // Fetch only AVAILABLE vehicles from DB first (uses idx_vehicle_status)
        return vehicleRepository.findByStatus(VehicleStatus.AVAILABLE).stream()
            .filter(v -> v.getCurrentLatitude() != null && v.getCurrentLongitude() != null)
            .map(v -> {
                double dist = HaversineCalculator.calculateDistance(lat, lon,
                        v.getCurrentLatitude(), v.getCurrentLongitude());
                NearbyVehicleResponseDto dto = new NearbyVehicleResponseDto();
                dto.setVehicleId(v.getId());
                dto.setRegistrationNumber(v.getRegistrationNumber());
                dto.setType(v.getType().name());
                dto.setDistanceKm(Math.round(dist * 100.0) / 100.0); // 2 decimal places
                return dto;
            })
            .filter(dto -> dto.getDistanceKm() <= radiusKm)
            .sorted((a, b) -> Double.compare(a.getDistanceKm(), b.getDistanceKm()))
            .collect(Collectors.toList());
    }

    public Vehicle findVehicleById(Long id) {
        return vehicleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));
    }
}
