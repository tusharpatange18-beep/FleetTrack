package com.fleettrack.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NearbyVehicleResponseDto {
    private Long vehicleId;
    private String registrationNumber;
    private String type;
    private Double distanceKm;
    private Long driverId;
    private LocalDateTime locationUpdatedAt;
}
