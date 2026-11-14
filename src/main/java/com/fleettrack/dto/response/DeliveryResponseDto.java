package com.fleettrack.dto.response;

import lombok.Data;

@Data
public class DeliveryResponseDto {
    private Long id;
    private Long vehicleId;
    private Long driverId;
    private Long customerId;
    private String status;
    private Double pickupLatitude;
    private Double pickupLongitude;
    private Double dropLatitude;
    private Double dropLongitude;
}
