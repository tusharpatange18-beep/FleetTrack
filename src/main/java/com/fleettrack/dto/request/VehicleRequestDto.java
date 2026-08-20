package com.fleettrack.dto.request;

import com.fleettrack.entity.enums.VehicleType;
import com.fleettrack.entity.enums.VehicleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VehicleRequestDto {
    @NotBlank
    private String registrationNumber;
    @NotNull
    private VehicleType type;
    @NotNull
    private VehicleStatus status;
}
