package com.fleettrack.dto.request;

import com.fleettrack.entity.enums.DeliveryStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeliveryStatusUpdateRequestDto {
    @NotNull
    private DeliveryStatus status;
}
