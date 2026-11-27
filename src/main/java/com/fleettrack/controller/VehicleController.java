package com.fleettrack.controller;

import com.fleettrack.dto.request.LocationUpdateRequestDto;
import com.fleettrack.dto.request.VehicleRequestDto;
import com.fleettrack.dto.request.VehicleUpdateRequestDto;
import com.fleettrack.dto.response.LocationHistoryResponseDto;
import com.fleettrack.dto.response.NearbyVehicleResponseDto;
import com.fleettrack.dto.response.VehicleResponseDto;
import com.fleettrack.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * HTTP endpoints for vehicles. Controllers stay thin: they read the request and call
 * VehicleService, where all the rules live. @Valid checks the request body; @Validated
 * turns on the checks written on parameters (e.g. @DecimalMin on latitude).
 */
@Tag(name = "Vehicles", description = "Fleet vehicle management")
@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
@Validated
public class VehicleController {

    private final VehicleService vehicleService;

    @Operation(summary = "List all vehicles (paginated)")
    @GetMapping
    public Page<VehicleResponseDto> getVehicles(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return vehicleService.getVehicles(pageable);
    }

    @Operation(summary = "Create a new vehicle (status defaults to AVAILABLE)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleResponseDto createVehicle(@Valid @RequestBody VehicleRequestDto request) {
        return vehicleService.createVehicle(request);
    }

    @Operation(summary = "Get vehicle by ID")
    @GetMapping("/{id}")
    public VehicleResponseDto getVehicle(@PathVariable Long id) {
        return vehicleService.getVehicle(id);
    }

    @Operation(summary = "Partially update a vehicle (registration, type, AVAILABLE/OFFLINE status)")
    @PatchMapping("/{id}")
    public VehicleResponseDto updateVehicle(@PathVariable Long id, @Valid @RequestBody VehicleUpdateRequestDto request) {
        return vehicleService.updateVehicle(id, request);
    }

    @Operation(summary = "Soft-delete (deactivate) a vehicle and release its driver")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVehicle(@PathVariable Long id) {
        vehicleService.deleteVehicle(id);
    }

    @Operation(summary = "Update vehicle current location and record history")
    @PatchMapping("/{id}/location")
    public VehicleResponseDto updateLocation(@PathVariable Long id,
                                             @Valid @RequestBody LocationUpdateRequestDto request) {
        return vehicleService.updateLocation(id, request);
    }

    @Operation(summary = "Location history of a vehicle, newest first (optional ISO-8601 from/to window)")
    @GetMapping("/{id}/locations")
    public Page<LocationHistoryResponseDto> getLocationHistory(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 50) Pageable pageable) {
        return vehicleService.getLocationHistory(id, from, to, pageable);
    }

    @Operation(summary = "Find nearby AVAILABLE vehicles by Haversine distance, nearest first")
    @GetMapping("/nearby")
    public List<NearbyVehicleResponseDto> getNearbyVehicles(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,
            @RequestParam(defaultValue = "10.0") @Positive @DecimalMax("500") double radiusKm) {
        return vehicleService.findNearbyVehicles(latitude, longitude, radiusKm);
    }
}
