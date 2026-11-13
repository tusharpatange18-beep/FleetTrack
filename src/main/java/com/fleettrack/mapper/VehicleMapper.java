package com.fleettrack.mapper;

import com.fleettrack.dto.request.VehicleRequestDto;
import com.fleettrack.dto.response.LocationHistoryResponseDto;
import com.fleettrack.dto.response.VehicleResponseDto;
import com.fleettrack.entity.LocationHistory;
import com.fleettrack.entity.Vehicle;
import com.fleettrack.entity.enums.VehicleStatus;
import org.springframework.stereotype.Component;

@Component
public class VehicleMapper {

    public Vehicle toEntity(VehicleRequestDto dto) {
        Vehicle vehicle = new Vehicle();
        vehicle.setRegistrationNumber(dto.getRegistrationNumber().trim());
        vehicle.setType(dto.getType());
        vehicle.setStatus(dto.getStatus() != null ? dto.getStatus() : VehicleStatus.AVAILABLE);
        return vehicle;
    }

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

    public LocationHistoryResponseDto toDto(LocationHistory history) {
        return new LocationHistoryResponseDto(history.getId(), history.getLatitude(),
                history.getLongitude(), history.getRecordedAt());
    }
}
