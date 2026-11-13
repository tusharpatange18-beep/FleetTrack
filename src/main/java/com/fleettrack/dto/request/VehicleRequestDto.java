package com.fleettrack.dto.request;

import com.fleettrack.entity.enums.VehicleStatus;
import com.fleettrack.entity.enums.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Body for creating a vehicle. */
@Data
public class VehicleRequestDto {
    @NotBlank
    @Size(max = 100)
    private String registrationNumber;

    @NotNull
    private VehicleType type;

    /** Optional, defaults to AVAILABLE. Only AVAILABLE or OFFLINE are accepted; BUSY is set by the system. */
    private VehicleStatus status;
}
