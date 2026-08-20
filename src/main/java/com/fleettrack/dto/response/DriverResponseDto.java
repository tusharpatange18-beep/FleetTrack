package com.fleettrack.dto.response;

import lombok.Data;

@Data
public class DriverResponseDto {
    private Long id;
    private String name;
    private String phone;
    private String licenseNumber;
    private String status;
}
