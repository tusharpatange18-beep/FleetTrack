package com.fleettrack.dto.request;

import com.fleettrack.entity.enums.VehicleStatus;
import com.fleettrack.entity.enums.VehicleType;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body for PATCH /api/vehicles/{id}. Every field is optional; only the fields sent are changed.
 * Status may be set to AVAILABLE or OFFLINE only, and not while the vehicle is on a delivery.
 */
@Data
public class VehicleUpdateRequestDto {
    @Size(min = 1, max = 100)
    private String registrationNumber;

    private VehicleType type;

    private VehicleStatus status;
}
