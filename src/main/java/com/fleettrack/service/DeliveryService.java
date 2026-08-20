package com.fleettrack.service;

import com.fleettrack.dto.request.DeliveryRequestDto;
import com.fleettrack.dto.request.DeliveryStatusUpdateRequestDto;
import com.fleettrack.dto.response.DeliveryResponseDto;
import com.fleettrack.entity.Customer;
import com.fleettrack.entity.Delivery;
import com.fleettrack.entity.Vehicle;
import com.fleettrack.entity.enums.DeliveryStatus;
import com.fleettrack.entity.enums.VehicleStatus;
import com.fleettrack.exception.ConflictException;
import com.fleettrack.exception.ResourceNotFoundException;
import com.fleettrack.mapper.DeliveryMapper;
import com.fleettrack.repository.CustomerRepository;
import com.fleettrack.repository.DeliveryRepository;
import com.fleettrack.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    private final DeliveryRepository deliveryRepository;
    private final CustomerRepository customerRepository;
    private final VehicleRepository vehicleRepository;
    private final DeliveryMapper deliveryMapper;

    public Page<DeliveryResponseDto> getDeliveries(Pageable pageable) {
        return deliveryRepository.findAll(pageable).map(deliveryMapper::toDto);
    }

    @Transactional
    public DeliveryResponseDto createDelivery(DeliveryRequestDto requestDto) {
        Customer customer = customerRepository.findById(requestDto.getCustomerId())
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + requestDto.getCustomerId()));

        Delivery delivery = new Delivery();
        delivery.setCustomer(customer);
        delivery.setStatus(DeliveryStatus.CREATED);

        Delivery saved = deliveryRepository.save(delivery);
        log.info("Created delivery id={} for customer id={}", saved.getId(), requestDto.getCustomerId());
        return deliveryMapper.toDto(saved);
    }

    public DeliveryResponseDto getDelivery(Long id) {
        return deliveryMapper.toDto(findDeliveryById(id));
    }

    /**
     * Assign a vehicle (with driver) to a delivery.
     * Business rules:
     * - Delivery must be in CREATED state.
     * - Vehicle must be AVAILABLE.
     * - Vehicle must have an assigned driver.
     * - Vehicle must not already be in an active delivery.
     * After assignment: delivery -> ASSIGNED, vehicle -> BUSY.
     */
    @Transactional
    public DeliveryResponseDto assignDelivery(Long deliveryId, Long vehicleId) {
        Delivery delivery = findDeliveryById(deliveryId);
        if (delivery.getStatus() != DeliveryStatus.CREATED) {
            throw new ConflictException("Delivery must be in CREATED state to assign, current state: " + delivery.getStatus());
        }

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + vehicleId));

        if (vehicle.getStatus() != VehicleStatus.AVAILABLE) {
            throw new ConflictException("Vehicle is not AVAILABLE (status: " + vehicle.getStatus() + ")");
        }

        if (vehicle.getDriver() == null) {
            throw new ConflictException("Vehicle does not have an assigned driver");
        }

        // Extra safety: ensure no other active delivery uses this vehicle
        if (deliveryRepository.existsByVehicleIdAndStatusIn(vehicleId,
                List.of(DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT))) {
            throw new ConflictException("Vehicle is already assigned to another active delivery");
        }

        delivery.setVehicle(vehicle);
        delivery.setDriver(vehicle.getDriver());
        delivery.setStatus(DeliveryStatus.ASSIGNED);

        vehicle.setStatus(VehicleStatus.BUSY);
        vehicleRepository.save(vehicle);

        Delivery saved = deliveryRepository.save(delivery);
        log.info("Assigned vehicle id={} (driver id={}) to delivery id={}", vehicleId, vehicle.getDriver().getId(), deliveryId);
        return deliveryMapper.toDto(saved);
    }

    /**
     * Update delivery status following the state machine.
     * Valid transitions:
     *   CREATED   -> ASSIGNED (via assignDelivery)
     *   ASSIGNED  -> PICKED_UP
     *   PICKED_UP -> IN_TRANSIT
     *   IN_TRANSIT-> DELIVERED
     *   CREATED   -> CANCELLED
     *   ASSIGNED  -> CANCELLED
     *
     * On DELIVERED or CANCELLED: vehicle is released back to AVAILABLE.
     */
    @Transactional
    public DeliveryResponseDto updateStatus(Long id, DeliveryStatusUpdateRequestDto request) {
        Delivery delivery = findDeliveryById(id);
        DeliveryStatus oldStatus = delivery.getStatus();
        DeliveryStatus newStatus = request.getStatus();

        if (!isValidTransition(oldStatus, newStatus)) {
            throw new ConflictException("Invalid state transition from " + oldStatus + " to " + newStatus);
        }

        delivery.setStatus(newStatus);

        // Release vehicle when delivery is terminal
        if ((newStatus == DeliveryStatus.DELIVERED || newStatus == DeliveryStatus.CANCELLED)
                && delivery.getVehicle() != null) {
            Vehicle vehicle = delivery.getVehicle();
            vehicle.setStatus(VehicleStatus.AVAILABLE);
            vehicleRepository.save(vehicle);
            log.info("Released vehicle id={} back to AVAILABLE after delivery id={} reached status {}", vehicle.getId(), id, newStatus);
        }

        Delivery saved = deliveryRepository.save(delivery);
        log.info("Delivery id={} status changed from {} to {}", id, oldStatus, newStatus);
        return deliveryMapper.toDto(saved);
    }

    private boolean isValidTransition(DeliveryStatus from, DeliveryStatus to) {
        // Cancellation allowed from CREATED or ASSIGNED only
        if (to == DeliveryStatus.CANCELLED) {
            return from == DeliveryStatus.CREATED || from == DeliveryStatus.ASSIGNED;
        }
        // Normal forward progression
        return switch (from) {
            case CREATED   -> to == DeliveryStatus.ASSIGNED;
            case ASSIGNED  -> to == DeliveryStatus.PICKED_UP;
            case PICKED_UP -> to == DeliveryStatus.IN_TRANSIT;
            case IN_TRANSIT-> to == DeliveryStatus.DELIVERED;
            default        -> false; // DELIVERED, CANCELLED are terminal
        };
    }

    private Delivery findDeliveryById(Long id) {
        return deliveryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + id));
    }
}
