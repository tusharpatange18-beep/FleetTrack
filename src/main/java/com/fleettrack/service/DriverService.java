package com.fleettrack.service;

import com.fleettrack.dto.request.DriverRequestDto;
import com.fleettrack.dto.response.DriverResponseDto;
import com.fleettrack.entity.Driver;
import com.fleettrack.entity.Vehicle;
import com.fleettrack.entity.enums.DriverStatus;
import com.fleettrack.entity.enums.VehicleStatus;
import com.fleettrack.exception.ConflictException;
import com.fleettrack.exception.ResourceNotFoundException;
import com.fleettrack.mapper.DriverMapper;
import com.fleettrack.repository.DeliveryRepository;
import com.fleettrack.repository.DriverRepository;
import com.fleettrack.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Business rules for drivers: create, update, soft delete, and linking a driver to a vehicle. */
@Service
@RequiredArgsConstructor          // Lombok: creates a constructor for all the final fields below
@Transactional(readOnly = true)   // every method runs in a read-only transaction unless it says otherwise
public class DriverService {

    private static final Logger log = LoggerFactory.getLogger(DriverService.class);

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final DeliveryRepository deliveryRepository;
    private final DriverMapper driverMapper;

    /** One page of drivers, converted to response DTOs. */
    public Page<DriverResponseDto> getDrivers(Pageable pageable) {
        Page<Driver> page = driverRepository.findAll(pageable);
        // Page.map converts every Driver on the page with driverMapper.toDto
        return page.map(driverMapper::toDto);
    }

    @Transactional
    public DriverResponseDto createDriver(DriverRequestDto requestDto) {
        // Phone and license number must both be unique
        if (driverRepository.existsByPhone(requestDto.getPhone())) {
            throw new ConflictException("A driver with this phone number already exists");
        }
        if (driverRepository.existsByLicenseNumber(requestDto.getLicenseNumber())) {
            throw new ConflictException("A driver with this license number already exists");
        }
        Driver newDriver = driverMapper.toEntity(requestDto);
        Driver saved = driverRepository.save(newDriver);
        log.info("Created driver id={}", saved.getId());
        return driverMapper.toDto(saved);
    }

    public DriverResponseDto getDriver(Long id) {
        Driver driver = findDriverById(id);
        return driverMapper.toDto(driver);
    }

    @Transactional
    public DriverResponseDto updateDriver(Long id, DriverRequestDto requestDto) {
        Driver driver = findDriverById(id);

        // A changed phone number must not belong to another driver
        boolean phoneChanged = !driver.getPhone().equals(requestDto.getPhone());
        if (phoneChanged && driverRepository.existsByPhone(requestDto.getPhone())) {
            throw new ConflictException("A driver with this phone number already exists");
        }
        // Same rule for the license number
        boolean licenseChanged = !driver.getLicenseNumber().equals(requestDto.getLicenseNumber());
        if (licenseChanged && driverRepository.existsByLicenseNumber(requestDto.getLicenseNumber())) {
            throw new ConflictException("A driver with this license number already exists");
        }

        // A driver who is being made INACTIVE must give up their vehicle
        boolean becomingInactive = requestDto.getStatus() == DriverStatus.INACTIVE
                && driver.getStatus() != DriverStatus.INACTIVE;
        if (becomingInactive) {
            releaseFromVehicle(driver);
        }

        // Copy the new values; JPA saves them when the transaction commits
        driver.setName(requestDto.getName());
        driver.setPhone(requestDto.getPhone());
        driver.setLicenseNumber(requestDto.getLicenseNumber());
        driver.setStatus(requestDto.getStatus());
        return driverMapper.toDto(driver);
    }

    /** Soft delete: the driver becomes INACTIVE and is unlinked from their vehicle. */
    @Transactional
    public void deleteDriver(Long id) {
        Driver driver = findDriverById(id);
        releaseFromVehicle(driver);
        driver.setStatus(DriverStatus.INACTIVE);
        log.info("Soft-deleted (deactivated) driver id={}", id);
    }

    /**
     * Links a driver to a vehicle. The vehicle stays AVAILABLE; it becomes BUSY only when a
     * delivery is assigned. Rules: driver ACTIVE, driver not on another vehicle, vehicle
     * AVAILABLE and without a driver. The vehicle row is locked; the UNIQUE constraint on
     * vehicle.driver_id catches the remaining race (same driver to two vehicles at once).
     */
    @Transactional
    public void assignVehicle(Long driverId, Long vehicleId) {
        Driver driver = findDriverById(driverId);
        if (driver.getStatus() != DriverStatus.ACTIVE) {
            throw new ConflictException("Driver is not active and cannot be assigned");
        }
        if (vehicleRepository.existsByDriverId(driverId)) {
            throw new ConflictException("Driver is already assigned to another vehicle");
        }

        Vehicle vehicle = vehicleRepository.findByIdForUpdate(vehicleId)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + vehicleId));
        if (vehicle.getStatus() != VehicleStatus.AVAILABLE) {
            throw new ConflictException("Vehicle is not AVAILABLE (status: " + vehicle.getStatus() + ")");
        }
        if (vehicle.getDriver() != null) {
            throw new ConflictException("Vehicle already has a driver assigned (driver id: " + vehicle.getDriver().getId() + ")");
        }

        vehicle.setDriver(driver);
        vehicleRepository.flush(); // surface a unique-constraint race here, inside the transaction
        log.info("Assigned driver id={} to vehicle id={}", driverId, vehicleId);
    }

    /** Unlinks a driver from their vehicle. Not allowed while the vehicle is on a delivery. */
    @Transactional
    public void unassignVehicle(Long driverId) {
        Driver driver = findDriverById(driverId);
        // orElseThrow: use the vehicle if there is one, otherwise answer 409 Conflict
        Vehicle vehicle = vehicleRepository.findByDriverId(driverId)
            .orElseThrow(() -> new ConflictException("Driver is not assigned to any vehicle"));
        detach(driver, vehicle);
    }

    /** If the driver is linked to a vehicle, unlink them. Does nothing if they have no vehicle. */
    private void releaseFromVehicle(Driver driver) {
        // Optional = "maybe a vehicle, maybe nothing"
        Optional<Vehicle> vehicle = vehicleRepository.findByDriverId(driver.getId());
        if (vehicle.isPresent()) {
            detach(driver, vehicle.get());
        }
    }

    /** Unlinks driver and vehicle. Refused while the vehicle is on an active delivery. */
    private void detach(Driver driver, Vehicle vehicle) {
        // Lock the vehicle row so a delivery cannot be assigned to it at the same moment
        Vehicle locked = vehicleRepository.findByIdForUpdate(vehicle.getId()).orElseThrow();
        if (deliveryRepository.existsByVehicleIdAndStatusIn(locked.getId(), DeliveryService.ACTIVE_STATUSES)) {
            throw new ConflictException("The driver's vehicle is on an active delivery; finish or cancel it first");
        }
        locked.setDriver(null);
        log.info("Unassigned driver id={} from vehicle id={}", driver.getId(), locked.getId());
    }

    /** orElseThrow: return the driver if found, otherwise throw a 404 error. */
    public Driver findDriverById(Long id) {
        return driverRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + id));
    }
}
