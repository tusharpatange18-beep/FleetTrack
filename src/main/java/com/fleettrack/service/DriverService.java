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
import com.fleettrack.repository.DriverRepository;
import com.fleettrack.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DriverService {

    private static final Logger log = LoggerFactory.getLogger(DriverService.class);

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverMapper driverMapper;

    public Page<DriverResponseDto> getDrivers(Pageable pageable) {
        return driverRepository.findAll(pageable).map(driverMapper::toDto);
    }

    public DriverResponseDto createDriver(DriverRequestDto requestDto) {
        Driver driver = driverMapper.toEntity(requestDto);
        Driver saved = driverRepository.save(driver);
        log.info("Created driver id={}, name={}", saved.getId(), saved.getName());
        return driverMapper.toDto(saved);
    }

    public DriverResponseDto getDriver(Long id) {
        return driverMapper.toDto(findDriverById(id));
    }

    @Transactional
    public DriverResponseDto updateDriver(Long id, DriverRequestDto requestDto) {
        Driver driver = findDriverById(id);
        driver.setName(requestDto.getName());
        driver.setPhone(requestDto.getPhone());
        driver.setLicenseNumber(requestDto.getLicenseNumber());
        driver.setStatus(requestDto.getStatus());
        return driverMapper.toDto(driverRepository.save(driver));
    }

    @Transactional
    public void deleteDriver(Long id) {
        Driver driver = findDriverById(id);
        driver.setStatus(DriverStatus.INACTIVE);
        driverRepository.save(driver);
        log.info("Soft-deleted (deactivated) driver id={}", id);
    }

    /**
     * Assigns a driver to a vehicle. After assignment:
     * - The driver-vehicle link is established (driver_id FK on vehicle).
     * - The vehicle status remains AVAILABLE; it becomes BUSY only when a delivery is assigned.
     * Business rules enforced:
     * - Driver must be ACTIVE.
     * - Vehicle must be AVAILABLE.
     * - Vehicle must not already have a driver.
     * - Driver must not already be assigned to another vehicle.
     */
    @Transactional
    public void assignVehicle(Long driverId, Long vehicleId) {
        Driver driver = findDriverById(driverId);
        if (driver.getStatus() != DriverStatus.ACTIVE) {
            throw new ConflictException("Driver is not active and cannot be assigned");
        }

        // Check driver is not already assigned to another vehicle
        if (vehicleRepository.existsByDriverId(driverId)) {
            throw new ConflictException("Driver is already assigned to another vehicle");
        }

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + vehicleId));

        if (vehicle.getStatus() != VehicleStatus.AVAILABLE) {
            throw new ConflictException("Vehicle is not AVAILABLE (status: " + vehicle.getStatus() + ")");
        }

        if (vehicle.getDriver() != null) {
            throw new ConflictException("Vehicle already has a driver assigned (driver id: " + vehicle.getDriver().getId() + ")");
        }

        vehicle.setDriver(driver);
        // Vehicle remains AVAILABLE — it becomes BUSY only when assigned to a delivery
        vehicleRepository.save(vehicle);
        log.info("Assigned driver id={} to vehicle id={}. Vehicle status remains AVAILABLE.", driverId, vehicleId);
    }

    public Driver findDriverById(Long id) {
        return driverRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + id));
    }
}
