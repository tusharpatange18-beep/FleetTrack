package com.fleettrack.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeliveryRequestDto {
    @NotNull
    private Long customerId;
}
