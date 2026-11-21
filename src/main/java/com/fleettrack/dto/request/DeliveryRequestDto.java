package com.fleettrack.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Body for creating a delivery. The route is optional: any pair (pickup or drop) left out is
 * copied from the customer's saved addresses. A pair must be sent complete (both lat and lon).
 */
@Data
public class DeliveryRequestDto {
    @NotNull
    private Long customerId;

    @DecimalMin("-90") @DecimalMax("90")
    private Double pickupLatitude;
    @DecimalMin("-180") @DecimalMax("180")
    private Double pickupLongitude;
    @DecimalMin("-90") @DecimalMax("90")
    private Double dropLatitude;
    @DecimalMin("-180") @DecimalMax("180")
    private Double dropLongitude;

    @AssertTrue(message = "pickupLatitude and pickupLongitude must be sent together")
    public boolean isPickupComplete() {
        return (pickupLatitude == null) == (pickupLongitude == null);
    }

    @AssertTrue(message = "dropLatitude and dropLongitude must be sent together")
    public boolean isDropComplete() {
        return (dropLatitude == null) == (dropLongitude == null);
    }
}
