package com.fleettrack.mapper;

import com.fleettrack.dto.response.DeliveryResponseDto;
import com.fleettrack.entity.Delivery;
import org.springframework.stereotype.Component;

@Component
public class DeliveryMapper {
    public DeliveryResponseDto toDto(Delivery delivery) {
        DeliveryResponseDto dto = new DeliveryResponseDto();
        dto.setId(delivery.getId());
        if (delivery.getVehicle() != null) dto.setVehicleId(delivery.getVehicle().getId());
        if (delivery.getDriver() != null) dto.setDriverId(delivery.getDriver().getId());
        if (delivery.getCustomer() != null) dto.setCustomerId(delivery.getCustomer().getId());
        dto.setStatus(delivery.getStatus().name());
        return dto;
    }
}
