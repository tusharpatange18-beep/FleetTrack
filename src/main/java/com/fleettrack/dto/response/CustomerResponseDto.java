package com.fleettrack.dto.response;

import lombok.Data;

@Data
public class CustomerResponseDto {
    private Long id;
    private String name;
    private String phone;
    private Double pickupLatitude;
    private Double pickupLongitude;
    private Double dropLatitude;
    private Double dropLongitude;
}
