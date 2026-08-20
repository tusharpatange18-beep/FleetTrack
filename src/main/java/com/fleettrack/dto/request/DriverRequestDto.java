package com.fleettrack.dto.request;

import com.fleettrack.entity.enums.DriverStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DriverRequestDto {
    @NotBlank
    private String name;
    @NotBlank
    private String phone;
    @NotBlank
    private String licenseNumber;
    @NotNull
    private DriverStatus status;
}
