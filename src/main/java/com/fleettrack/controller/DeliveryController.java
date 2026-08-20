package com.fleettrack.controller;

import com.fleettrack.dto.request.DeliveryRequestDto;
import com.fleettrack.dto.request.DeliveryStatusUpdateRequestDto;
import com.fleettrack.dto.response.DeliveryResponseDto;
import com.fleettrack.service.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    @GetMapping
    public Page<DeliveryResponseDto> getDeliveries(Pageable pageable) {
        return deliveryService.getDeliveries(pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryResponseDto createDelivery(@Valid @RequestBody DeliveryRequestDto request) {
        return deliveryService.createDelivery(request);
    }

    @GetMapping("/{id}")
    public DeliveryResponseDto getDelivery(@PathVariable Long id) {
        return deliveryService.getDelivery(id);
    }

    @PatchMapping("/{id}/assign")
    public DeliveryResponseDto assignDelivery(@PathVariable Long id, @RequestParam Long vehicleId) {
        return deliveryService.assignDelivery(id, vehicleId);
    }

    @PatchMapping("/{id}/status")
    public DeliveryResponseDto updateStatus(@PathVariable Long id, @Valid @RequestBody DeliveryStatusUpdateRequestDto request) {
        return deliveryService.updateStatus(id, request);
    }
}
