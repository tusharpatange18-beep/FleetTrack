package com.fleettrack.controller;

import com.fleettrack.dto.request.LocationUpdateRequestDto;
import com.fleettrack.dto.request.VehicleRequestDto;
import com.fleettrack.dto.response.NearbyVehicleResponseDto;
import com.fleettrack.dto.response.VehicleResponseDto;
import com.fleettrack.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Vehicles", description = "Fleet vehicle management")
@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
@Validated
public class VehicleController {

    private final VehicleService vehicleService;

    @Operation(summary = "List all vehicles (paginated)")
    @GetMapping
    public Page<VehicleResponseDto> getVehicles(Pageable pageable) {
        return vehicleService.getVehicles(pageable);
    }

    @Operation(summary = "Create a new vehicle")
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

    @Operation(summary = "Update vehicle details")
    @PatchMapping("/{id}")
    public VehicleResponseDto updateVehicle(@PathVariable Long id, @Valid @RequestBody VehicleRequestDto request) {
        return vehicleService.updateVehicle(id, request);
    }

    @Operation(summary = "Soft-delete (deactivate) a vehicle")
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

    @Operation(summary = "Find nearby AVAILABLE vehicles by Haversine formula")
    @GetMapping("/nearby")
    public List<NearbyVehicleResponseDto> getNearbyVehicles(
            @RequestParam @Min(-90) @Max(90) double latitude,
            @RequestParam @Min(-180) @Max(180) double longitude,
            @RequestParam(defaultValue = "10.0") double radiusKm) {
        return vehicleService.findNearbyVehicles(latitude, longitude, radiusKm);
    }
}
