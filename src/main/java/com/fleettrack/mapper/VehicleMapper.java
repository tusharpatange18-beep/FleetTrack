package com.fleettrack.mapper;

import com.fleettrack.dto.request.VehicleRequestDto;
import com.fleettrack.dto.response.VehicleResponseDto;
import com.fleettrack.entity.Vehicle;
import org.springframework.stereotype.Component;

@Component
public class VehicleMapper {
    public Vehicle toEntity(VehicleRequestDto dto) {
        Vehicle vehicle = new Vehicle();
        vehicle.setRegistrationNumber(dto.getRegistrationNumber());
        vehicle.setType(dto.getType());
        vehicle.setStatus(dto.getStatus());
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
        if (vehicle.getDriver() != null) {
            dto.setDriverId(vehicle.getDriver().getId());
        }
        return dto;
    }
}
