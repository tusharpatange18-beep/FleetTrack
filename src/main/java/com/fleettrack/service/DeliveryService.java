package com.fleettrack.service;

import com.fleettrack.dto.request.DeliveryRequestDto;
import com.fleettrack.dto.request.DeliveryStatusUpdateRequestDto;
import com.fleettrack.dto.response.DeliveryResponseDto;
import com.fleettrack.entity.Customer;
import com.fleettrack.entity.Delivery;
import com.fleettrack.entity.Vehicle;
import com.fleettrack.entity.enums.DeliveryStatus;
import com.fleettrack.entity.enums.DriverStatus;
import com.fleettrack.entity.enums.VehicleStatus;
import com.fleettrack.exception.BadRequestException;
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

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    /** Statuses in which a delivery holds its vehicle. */
    public static final Set<DeliveryStatus> ACTIVE_STATUSES =
            EnumSet.of(DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT);

    /**
     * The delivery state machine. CREATED -> ASSIGNED happens only through assignDelivery,
     * because it needs a vehicle; the status endpoint handles every other edge.
     */
    private static final Map<DeliveryStatus, Set<DeliveryStatus>> TRANSITIONS = Map.of(
            DeliveryStatus.CREATED,    EnumSet.of(DeliveryStatus.ASSIGNED, DeliveryStatus.CANCELLED),
            DeliveryStatus.ASSIGNED,   EnumSet.of(DeliveryStatus.PICKED_UP, DeliveryStatus.CANCELLED),
            DeliveryStatus.PICKED_UP,  EnumSet.of(DeliveryStatus.IN_TRANSIT),
            DeliveryStatus.IN_TRANSIT, EnumSet.of(DeliveryStatus.DELIVERED),
            DeliveryStatus.DELIVERED,  EnumSet.noneOf(DeliveryStatus.class),
            DeliveryStatus.CANCELLED,  EnumSet.noneOf(DeliveryStatus.class)
    );

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
        log.info("Created delivery id={} for customer id={}", saved.getId(), customer.getId());
        return deliveryMapper.toDto(saved);
    }

    public DeliveryResponseDto getDelivery(Long id) {
        return deliveryMapper.toDto(findDeliveryById(id));
    }

    /**
     * Assigns a vehicle (and its driver) to a delivery.
     * Rules: delivery is CREATED; vehicle is AVAILABLE; vehicle has an ACTIVE driver; vehicle is
     * not on another active delivery. Afterwards: delivery ASSIGNED, vehicle BUSY.
     * Both rows are locked (delivery first, then vehicle, always in that order to avoid deadlocks).
     */
    @Transactional
    public DeliveryResponseDto assignDelivery(Long deliveryId, Long vehicleId) {
        Delivery delivery = lockDelivery(deliveryId);
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(vehicleId)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + vehicleId));
        assign(delivery, vehicle);
        return deliveryMapper.toDto(delivery);
    }

    /** Shared by manual and automatic assignment. Caller must hold locks on both rows. */
    void assign(Delivery delivery, Vehicle vehicle) {
        if (delivery.getStatus() != DeliveryStatus.CREATED) {
            throw new ConflictException("Delivery must be in CREATED state to assign, current state: " + delivery.getStatus());
        }
        if (vehicle.getStatus() != VehicleStatus.AVAILABLE) {
            throw new ConflictException("Vehicle is not AVAILABLE (status: " + vehicle.getStatus() + ")");
        }
        if (vehicle.getDriver() == null) {
            throw new ConflictException("Vehicle does not have an assigned driver");
        }
        if (vehicle.getDriver().getStatus() != DriverStatus.ACTIVE) {
            throw new ConflictException("The vehicle's driver is not ACTIVE");
        }
        // Defence in depth: the status check above should already guarantee this
        if (deliveryRepository.existsByVehicleIdAndStatusIn(vehicle.getId(), ACTIVE_STATUSES)) {
            throw new ConflictException("Vehicle is already assigned to another active delivery");
        }

        delivery.setVehicle(vehicle);
        delivery.setDriver(vehicle.getDriver());
        delivery.setStatus(DeliveryStatus.ASSIGNED);
        vehicle.setStatus(VehicleStatus.BUSY);
        log.info("Assigned vehicle id={} (driver id={}) to delivery id={}",
                vehicle.getId(), vehicle.getDriver().getId(), delivery.getId());
    }

    /**
     * Moves a delivery along the state machine. On DELIVERED or CANCELLED the vehicle is
     * released back to AVAILABLE.
     */
    @Transactional
    public DeliveryResponseDto updateStatus(Long id, DeliveryStatusUpdateRequestDto request) {
        DeliveryStatus newStatus = request.getStatus();
        if (newStatus == DeliveryStatus.ASSIGNED) {
            throw new BadRequestException("Use PATCH /api/deliveries/{id}/assign to assign a vehicle");
        }

        Delivery delivery = lockDelivery(id);
        DeliveryStatus oldStatus = delivery.getStatus();
        if (!isValidTransition(oldStatus, newStatus)) {
            throw new ConflictException("Invalid state transition from " + oldStatus + " to " + newStatus);
        }
        delivery.setStatus(newStatus);

        if (isTerminal(newStatus) && delivery.getVehicle() != null) {
            Vehicle vehicle = vehicleRepository.findByIdForUpdate(delivery.getVehicle().getId()).orElseThrow();
            // A vehicle deactivated meanwhile stays INACTIVE; only BUSY goes back to AVAILABLE
            if (vehicle.getStatus() == VehicleStatus.BUSY) {
                vehicle.setStatus(VehicleStatus.AVAILABLE);
            }
            log.info("Released vehicle id={} after delivery id={} reached {}", vehicle.getId(), id, newStatus);
        }

        log.info("Delivery id={} status changed from {} to {}", id, oldStatus, newStatus);
        return deliveryMapper.toDto(delivery);
    }

    static boolean isValidTransition(DeliveryStatus from, DeliveryStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    static boolean isTerminal(DeliveryStatus status) {
        return status == DeliveryStatus.DELIVERED || status == DeliveryStatus.CANCELLED;
    }

    private Delivery findDeliveryById(Long id) {
        return deliveryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + id));
    }

    Delivery lockDelivery(Long id) {
        return deliveryRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + id));
    }
}
