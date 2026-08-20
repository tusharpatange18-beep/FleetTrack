package com.fleettrack.dto.response;

import lombok.Data;

@Data
public class VehicleResponseDto {
    private Long id;
    private String registrationNumber;
    private String type;
    private String status;
    private Double currentLatitude;
    private Double currentLongitude;
    private Long driverId;
}
