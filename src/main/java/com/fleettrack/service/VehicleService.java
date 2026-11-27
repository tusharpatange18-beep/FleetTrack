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
import com.fleettrack.repository.DeliveryRepository;
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

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Business rules for vehicles: create, update, location pings, soft delete and nearby search. */
@Service
@RequiredArgsConstructor          // Lombok: creates a constructor for all the final fields below
@Transactional(readOnly = true)   // every method runs in a read-only transaction unless it says otherwise
public class VehicleService {

    private static final Logger log = LoggerFactory.getLogger(VehicleService.class);

    /** Statuses a user may set by hand. BUSY belongs to the delivery flow, INACTIVE to DELETE. */
    private static final Set<VehicleStatus> USER_SETTABLE_STATUSES = Set.of(VehicleStatus.AVAILABLE, VehicleStatus.OFFLINE);

    private final VehicleRepository vehicleRepository;
    private final LocationHistoryRepository locationHistoryRepository;
    private final DeliveryRepository deliveryRepository;
    private final VehicleMapper vehicleMapper;
    private final ProximitySearch proximitySearch;
    private final Clock clock;

    /** One page of vehicles, converted to response DTOs. */
    public Page<VehicleResponseDto> getVehicles(Pageable pageable) {
        Page<Vehicle> page = vehicleRepository.findAll(pageable);
        // Page.map converts every Vehicle on the page with vehicleMapper.toDto
        return page.map(vehicleMapper::toDto);
    }

    @Transactional
    public VehicleResponseDto createVehicle(VehicleRequestDto requestDto) {
        // A new vehicle may start AVAILABLE or OFFLINE only (no status given = AVAILABLE)
        if (requestDto.getStatus() != null && !USER_SETTABLE_STATUSES.contains(requestDto.getStatus())) {
            throw new BadRequestException("A new vehicle must be AVAILABLE or OFFLINE");
        }
        // Registration numbers must be unique (spaces at the ends are ignored)
        String registration = requestDto.getRegistrationNumber().trim();
        if (vehicleRepository.existsByRegistrationNumber(registration)) {
            throw new ConflictException("Vehicle with registration number already exists: " + registration);
        }
        Vehicle newVehicle = vehicleMapper.toEntity(requestDto);
        Vehicle saved = vehicleRepository.save(newVehicle);
        log.info("Created vehicle id={}, reg={}", saved.getId(), saved.getRegistrationNumber());
        return vehicleMapper.toDto(saved);
    }

    public VehicleResponseDto getVehicle(Long id) {
        Vehicle vehicle = findVehicleById(id);
        return vehicleMapper.toDto(vehicle);
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
            // Decide by the deliveries table, not the status field, so a vehicle left BUSY by
            // inconsistent data can still be repaired
            if (isOnActiveDelivery(vehicle)) {
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
        // 1) Save the new current position on the vehicle itself
        vehicle.setCurrentLatitude(requestDto.getLatitude());
        vehicle.setCurrentLongitude(requestDto.getLongitude());
        vehicle.setLocationUpdatedAt(now);

        // 2) Add a row to the location history, so the vehicle's trail can be shown later
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

        // No 'from' given: start from the very beginning (1 Jan 1970)
        LocalDateTime start = LocalDateTime.of(1970, 1, 1, 0, 0);
        if (from != null) {
            start = from;
        }
        // No 'to' given: go up to now (plus 1 minute, so a ping saved this second is included)
        LocalDateTime end = LocalDateTime.now(clock).plusMinutes(1);
        if (to != null) {
            end = to;
        }

        if (start.isAfter(end)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        // Spring Data builds this SQL query from the method name: vehicle id, time range, newest first
        Page<LocationHistory> page = locationHistoryRepository
                .findByVehicleIdAndRecordedAtBetweenOrderByRecordedAtDesc(id, start, end, pageable);
        // Page.map converts every row on the page with vehicleMapper.toDto
        return page.map(vehicleMapper::toDto);
    }

    /**
     * Soft delete: status becomes INACTIVE and the driver is released, so deliveries and
     * location history that reference this vehicle stay intact.
     */
    @Transactional
    public void deleteVehicle(Long id) {
        Vehicle vehicle = lockVehicle(id);
        if (isOnActiveDelivery(vehicle)) {
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
        // false = show vehicles even if they have no active driver (this is the dispatcher's map)
        List<ProximitySearch.Candidate> candidates = proximitySearch.nearestAvailable(lat, lon, radiusKm, false);

        // Turn each search result into the JSON object the API returns
        List<NearbyVehicleResponseDto> result = new ArrayList<>();
        for (ProximitySearch.Candidate candidate : candidates) {
            NearbyVehicleResponseDto dto = new NearbyVehicleResponseDto();
            dto.setVehicleId(candidate.vehicle().id());
            dto.setRegistrationNumber(candidate.vehicle().registrationNumber());
            dto.setType(candidate.vehicle().type().name());
            dto.setDistanceKm(HaversineCalculator.roundToTwoDecimals(candidate.distanceKm()));
            dto.setDriverId(candidate.vehicle().driverId());
            dto.setLocationUpdatedAt(candidate.vehicle().locationUpdatedAt());
            result.add(dto);
        }
        return result;
    }

    /** Normal read, no lock. orElseThrow: return the vehicle if found, otherwise throw a 404 error. */
    public Vehicle findVehicleById(Long id) {
        return vehicleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));
    }

    /** True if some delivery that is ASSIGNED, PICKED_UP or IN_TRANSIT uses this vehicle. */
    private boolean isOnActiveDelivery(Vehicle vehicle) {
        return deliveryRepository.existsByVehicleIdAndStatusIn(vehicle.getId(), DeliveryService.ACTIVE_STATUSES);
    }

    /** Read with SELECT ... FOR UPDATE, so no other transaction can change this row until we finish. */
    Vehicle lockVehicle(Long id) {
        return vehicleRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));
    }
}
