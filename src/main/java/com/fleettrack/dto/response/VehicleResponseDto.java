package com.fleettrack.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VehicleResponseDto {
    private Long id;
    private String registrationNumber;
    private String type;
    private String status;
    private Double currentLatitude;
    private Double currentLongitude;
    private LocalDateTime locationUpdatedAt;
    private Long driverId;
}
