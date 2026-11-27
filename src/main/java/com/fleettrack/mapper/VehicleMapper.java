package com.fleettrack.mapper;

import com.fleettrack.dto.request.VehicleRequestDto;
import com.fleettrack.dto.response.LocationHistoryResponseDto;
import com.fleettrack.dto.response.VehicleResponseDto;
import com.fleettrack.entity.LocationHistory;
import com.fleettrack.entity.Vehicle;
import com.fleettrack.entity.enums.VehicleStatus;
import org.springframework.stereotype.Component;

/**
 * Copies data between the database entity (Vehicle) and the JSON objects (DTOs) the API uses,
 * so the API never exposes the entity directly.
 */
@Component
public class VehicleMapper {

    /** Request JSON -> new Vehicle entity (not saved yet). */
    public Vehicle toEntity(VehicleRequestDto dto) {
        Vehicle vehicle = new Vehicle();
        vehicle.setRegistrationNumber(dto.getRegistrationNumber().trim());
        vehicle.setType(dto.getType());

        // No status in the request: a new vehicle starts AVAILABLE
        if (dto.getStatus() != null) {
            vehicle.setStatus(dto.getStatus());
        } else {
            vehicle.setStatus(VehicleStatus.AVAILABLE);
        }
        return vehicle;
    }

    /** Vehicle entity -> response JSON. */
    public VehicleResponseDto toDto(Vehicle vehicle) {
        VehicleResponseDto dto = new VehicleResponseDto();
        dto.setId(vehicle.getId());
        dto.setRegistrationNumber(vehicle.getRegistrationNumber());
        dto.setType(vehicle.getType().name());
        dto.setStatus(vehicle.getStatus().name());
        dto.setCurrentLatitude(vehicle.getCurrentLatitude());
        dto.setCurrentLongitude(vehicle.getCurrentLongitude());
        dto.setLocationUpdatedAt(vehicle.getLocationUpdatedAt());
        if (vehicle.getDriver() != null) {
            dto.setDriverId(vehicle.getDriver().getId());
        }
        return dto;
    }

    /** One location history row -> response JSON. */
    public LocationHistoryResponseDto toDto(LocationHistory history) {
        return new LocationHistoryResponseDto(history.getId(), history.getLatitude(),
                history.getLongitude(), history.getRecordedAt());
    }
}
