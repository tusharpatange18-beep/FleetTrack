package com.fleettrack.dto.response;

import lombok.Data;

@Data
public class NearbyVehicleResponseDto {
    private Long vehicleId;
    private String registrationNumber;
    private String type;
    private Double distanceKm;
}
