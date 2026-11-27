package com.fleettrack.controller;

import com.fleettrack.dto.request.DeliveryRequestDto;
import com.fleettrack.dto.request.DeliveryStatusUpdateRequestDto;
import com.fleettrack.dto.response.AutoAssignmentResponseDto;
import com.fleettrack.dto.response.DeliveryResponseDto;
import com.fleettrack.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for deliveries. All rules (state machine, locking) are in DeliveryService. */
@Tag(name = "Deliveries", description = "Delivery lifecycle and vehicle assignment")
@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
@Validated
public class DeliveryController {

    private final DeliveryService deliveryService;

    @Operation(summary = "List deliveries (paginated)")
    @GetMapping
    public Page<DeliveryResponseDto> getDeliveries(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return deliveryService.getDeliveries(pageable);
    }

    @Operation(summary = "Create a delivery; route defaults to the customer's saved addresses")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryResponseDto createDelivery(@Valid @RequestBody DeliveryRequestDto request) {
        return deliveryService.createDelivery(request);
    }

    @Operation(summary = "Get delivery by ID")
    @GetMapping("/{id}")
    public DeliveryResponseDto getDelivery(@PathVariable Long id) {
        return deliveryService.getDelivery(id);
    }

    @Operation(summary = "Assign a specific vehicle (and its driver) to a CREATED delivery")
    @PatchMapping("/{id}/assign")
    public DeliveryResponseDto assignDelivery(@PathVariable Long id, @RequestParam Long vehicleId) {
        return deliveryService.assignDelivery(id, vehicleId);
    }

    @Operation(summary = "Assign the nearest available vehicle with an active driver and a fresh location")
    @PostMapping("/{id}/auto-assign")
    public AutoAssignmentResponseDto autoAssign(
            @PathVariable Long id,
            @RequestParam(required = false) @Positive @DecimalMax("500") Double radiusKm) {
        return deliveryService.autoAssign(id, radiusKm);
    }

    @Operation(summary = "Move a delivery along its state machine (PICKED_UP, IN_TRANSIT, DELIVERED, CANCELLED)")
    @PatchMapping("/{id}/status")
    public DeliveryResponseDto updateStatus(@PathVariable Long id, @Valid @RequestBody DeliveryStatusUpdateRequestDto request) {
        return deliveryService.updateStatus(id, request);
    }
}
