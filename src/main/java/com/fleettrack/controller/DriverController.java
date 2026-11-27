package com.fleettrack.controller;

import com.fleettrack.dto.request.DriverRequestDto;
import com.fleettrack.dto.response.DriverResponseDto;
import com.fleettrack.service.DriverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for drivers. Only ADMIN may change drivers; DISPATCHER may read (see SecurityConfig). */
@Tag(name = "Drivers", description = "Driver management")
@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;

    @Operation(summary = "List all drivers (paginated)")
    @GetMapping
    public Page<DriverResponseDto> getDrivers(Pageable pageable) {
        return driverService.getDrivers(pageable);
    }

    @Operation(summary = "Create a new driver")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DriverResponseDto createDriver(@Valid @RequestBody DriverRequestDto request) {
        return driverService.createDriver(request);
    }

    @Operation(summary = "Get driver by ID")
    @GetMapping("/{id}")
    public DriverResponseDto getDriver(@PathVariable Long id) {
        return driverService.getDriver(id);
    }

    @Operation(summary = "Update driver details")
    @PatchMapping("/{id}")
    public DriverResponseDto updateDriver(@PathVariable Long id, @Valid @RequestBody DriverRequestDto request) {
        return driverService.updateDriver(id, request);
    }

    @Operation(summary = "Soft-delete (deactivate) a driver")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDriver(@PathVariable Long id) {
        driverService.deleteDriver(id);
    }

    @Operation(summary = "Assign a driver to a vehicle")
    @PatchMapping("/{driverId}/assign-vehicle/{vehicleId}")
    public void assignVehicle(@PathVariable Long driverId, @PathVariable Long vehicleId) {
        driverService.assignVehicle(driverId, vehicleId);
    }

    @Operation(summary = "Unassign a driver from their vehicle (not allowed during an active delivery)")
    @DeleteMapping("/{driverId}/vehicle")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassignVehicle(@PathVariable Long driverId) {
        driverService.unassignVehicle(driverId);
    }
}
