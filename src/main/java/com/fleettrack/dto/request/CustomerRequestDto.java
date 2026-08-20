package com.fleettrack.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CustomerRequestDto {
    @NotBlank
    private String name;
    @NotBlank
    private String phone;
    @NotNull @Min(-90) @Max(90)
    private Double pickupLatitude;
    @NotNull @Min(-180) @Max(180)
    private Double pickupLongitude;
    @NotNull @Min(-90) @Max(90)
    private Double dropLatitude;
    @NotNull @Min(-180) @Max(180)
    private Double dropLongitude;
}
