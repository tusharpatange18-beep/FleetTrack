package com.fleettrack.service;

import com.fleettrack.dto.request.LocationUpdateRequestDto;
import com.fleettrack.dto.request.VehicleRequestDto;
import com.fleettrack.dto.request.VehicleUpdateRequestDto;
import com.fleettrack.dto.response.LocationHistoryResponseDto;
import com.fleettrack.dto.response.NearbyVehicleResponseDto;
import com.fleettrack.dto.response.VehicleResponseDto;
import com.fleettrack.entity.LocationHistory;
import com.fleettrack.entity.Vehicle;
import com.fleettrack.entity.enums.VehicleStatus;
import com.fleettrack.exception.BadRequestException;
import com.fleettrack.exception.ConflictException;
import com.fleettrack.exception.ResourceNotFoundException;
import com.fleettrack.mapper.VehicleMapper;
import com.fleettrack.repository.LocationHistoryRepository;
import com.fleettrack.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleService {

    private static final Logger log = LoggerFactory.getLogger(VehicleService.class);

    /** Statuses a user may set by hand. BUSY belongs to the delivery flow, INACTIVE to DELETE. */
    private static final Set<VehicleStatus> USER_SETTABLE_STATUSES = Set.of(VehicleStatus.AVAILABLE, VehicleStatus.OFFLINE);

    private final VehicleRepository vehicleRepository;
    private final LocationHistoryRepository locationHistoryRepository;
    private final VehicleMapper vehicleMapper;
    private final ProximitySearch proximitySearch;
    private final Clock clock;

    public Page<VehicleResponseDto> getVehicles(Pageable pageable) {
        return vehicleRepository.findAll(pageable).map(vehicleMapper::toDto);
    }

    @Transactional
    public VehicleResponseDto createVehicle(VehicleRequestDto requestDto) {
        if (requestDto.getStatus() != null && !USER_SETTABLE_STATUSES.contains(requestDto.getStatus())) {
            throw new BadRequestException("A new vehicle must be AVAILABLE or OFFLINE");
        }
        String registration = requestDto.getRegistrationNumber().trim();
        if (vehicleRepository.existsByRegistrationNumber(registration)) {
            throw new ConflictException("Vehicle with registration number already exists: " + registration);
        }
        Vehicle saved = vehicleRepository.save(vehicleMapper.toEntity(requestDto));
        log.info("Created vehicle id={}, reg={}", saved.getId(), saved.getRegistrationNumber());
        return vehicleMapper.toDto(saved);
    }

    public VehicleResponseDto getVehicle(Long id) {
        return vehicleMapper.toDto(findVehicleById(id));
    }

    /**
     * Partial update: only non-null fields are applied.
     * The row is locked so a concurrent delivery assignment cannot interleave with a status change.
     */
    @Transactional
    public VehicleResponseDto updateVehicle(Long id, VehicleUpdateRequestDto requestDto) {
        Vehicle vehicle = lockVehicle(id);

        if (requestDto.getRegistrationNumber() != null) {
            String registration = requestDto.getRegistrationNumber().trim();
            if (registration.isEmpty()) {
                throw new BadRequestException("registrationNumber must not be blank");
            }
            if (!vehicle.getRegistrationNumber().equals(registration)
                    && vehicleRepository.existsByRegistrationNumber(registration)) {
                throw new ConflictException("Registration number already in use: " + registration);
            }
            vehicle.setRegistrationNumber(registration);
        }

        if (requestDto.getType() != null) {
            vehicle.setType(requestDto.getType());
        }

        VehicleStatus newStatus = requestDto.getStatus();
        if (newStatus != null && newStatus != vehicle.getStatus()) {
            if (!USER_SETTABLE_STATUSES.contains(newStatus)) {
                throw new BadRequestException("Status can only be set to AVAILABLE or OFFLINE; "
                        + "BUSY is set by delivery assignment and INACTIVE by DELETE");
            }
            if (vehicle.getStatus() == VehicleStatus.BUSY) {
                throw new ConflictException("Vehicle is on an active delivery; finish or cancel it first");
            }
            vehicle.setStatus(newStatus);
        }
        return vehicleMapper.toDto(vehicle);
    }

    /**
     * Updates the vehicle's current position and appends a LocationHistory row in one transaction.
     * Not row-locked on purpose: pings are frequent, and @DynamicUpdate on Vehicle means this
     * UPDATE touches only the location columns, so it cannot overwrite a concurrent status change.
     */
    @Transactional
    public VehicleResponseDto updateLocation(Long id, LocationUpdateRequestDto requestDto) {
        Vehicle vehicle = findVehicleById(id);
        if (vehicle.getStatus() == VehicleStatus.INACTIVE) {
            throw new ConflictException("Vehicle is deactivated");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        vehicle.setCurrentLatitude(requestDto.getLatitude());
        vehicle.setCurrentLongitude(requestDto.getLongitude());
        vehicle.setLocationUpdatedAt(now);

        LocationHistory history = new LocationHistory();
        history.setVehicle(vehicle);
        history.setLatitude(requestDto.getLatitude());
        history.setLongitude(requestDto.getLongitude());
        history.setRecordedAt(now);
        locationHistoryRepository.save(history);

        log.debug("Updated location for vehicle id={}: lat={}, lon={}", id, requestDto.getLatitude(), requestDto.getLongitude());
        return vehicleMapper.toDto(vehicle);
    }

    /** Location trail of one vehicle, newest first, optionally limited to a time window. */
    public Page<LocationHistoryResponseDto> getLocationHistory(Long id, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        findVehicleById(id); // 404 for unknown vehicles instead of an empty page
        LocalDateTime start = from != null ? from : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime end = to != null ? to : LocalDateTime.now(clock).plusMinutes(1);
        if (start.isAfter(end)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        return locationHistoryRepository
                .findByVehicleIdAndRecordedAtBetweenOrderByRecordedAtDesc(id, start, end, pageable)
                .map(vehicleMapper::toDto);
    }

    /**
     * Soft delete: status becomes INACTIVE and the driver is released, so deliveries and
     * location history that reference this vehicle stay intact.
     */
    @Transactional
    public void deleteVehicle(Long id) {
        Vehicle vehicle = lockVehicle(id);
        if (vehicle.getStatus() == VehicleStatus.BUSY) {
            throw new ConflictException("Vehicle is on an active delivery; finish or cancel it first");
        }
        vehicle.setStatus(VehicleStatus.INACTIVE);
        vehicle.setDriver(null);
        log.info("Soft-deleted vehicle id={}", id);
    }

    /**
     * AVAILABLE vehicles with a fresh location within radiusKm, nearest first.
     * See {@link ProximitySearch} for the bounding-box + Haversine approach.
     */
    public List<NearbyVehicleResponseDto> findNearbyVehicles(double lat, double lon, double radiusKm) {
        return proximitySearch.nearestAvailable(lat, lon, radiusKm, false).stream()
            .map(c -> {
                NearbyVehicleResponseDto dto = new NearbyVehicleResponseDto();
                dto.setVehicleId(c.vehicle().id());
                dto.setRegistrationNumber(c.vehicle().registrationNumber());
                dto.setType(c.vehicle().type().name());
                dto.setDistanceKm(Math.round(c.distanceKm() * 100.0) / 100.0);
                dto.setDriverId(c.vehicle().driverId());
                dto.setLocationUpdatedAt(c.vehicle().locationUpdatedAt());
                return dto;
            })
            .toList();
    }

    public Vehicle findVehicleById(Long id) {
        return vehicleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));
    }

    Vehicle lockVehicle(Long id) {
        return vehicleRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));
    }
}
