package com.fleettrack.mapper;

import com.fleettrack.dto.response.DeliveryResponseDto;
import com.fleettrack.entity.Delivery;
import org.springframework.stereotype.Component;

/** Copies a Delivery entity into the JSON object the API returns. */
@Component
public class DeliveryMapper {

    /** Delivery entity -> response JSON. Linked rows are sent as ids only. */
    public DeliveryResponseDto toDto(Delivery delivery) {
        DeliveryResponseDto dto = new DeliveryResponseDto();
        dto.setId(delivery.getId());

        // getId() on a lazy proxy does not trigger a database load.
        // Vehicle and driver are empty until the delivery is assigned.
        if (delivery.getVehicle() != null) {
            dto.setVehicleId(delivery.getVehicle().getId());
        }
        if (delivery.getDriver() != null) {
            dto.setDriverId(delivery.getDriver().getId());
        }
        if (delivery.getCustomer() != null) {
            dto.setCustomerId(delivery.getCustomer().getId());
        }

        dto.setStatus(delivery.getStatus().name());
        dto.setPickupLatitude(delivery.getPickupLatitude());
        dto.setPickupLongitude(delivery.getPickupLongitude());
        dto.setDropLatitude(delivery.getDropLatitude());
        dto.setDropLongitude(delivery.getDropLongitude());
        return dto;
    }
}
