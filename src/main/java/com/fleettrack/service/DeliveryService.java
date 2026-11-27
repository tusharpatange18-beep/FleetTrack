package com.fleettrack.service;

import com.fleettrack.config.FleetProperties;
import com.fleettrack.dto.request.DeliveryRequestDto;
import com.fleettrack.dto.request.DeliveryStatusUpdateRequestDto;
import com.fleettrack.dto.response.AutoAssignmentResponseDto;
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
import com.fleettrack.util.HaversineCalculator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All business rules for deliveries: creating them, assigning a vehicle, and moving them
 * through the status state machine.
 */
@Service
@RequiredArgsConstructor          // Lombok: creates a constructor for all the final fields below
@Transactional(readOnly = true)   // every method runs in a read-only transaction unless it says otherwise
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    /** Statuses in which a delivery is still using its vehicle. */
    public static final Set<DeliveryStatus> ACTIVE_STATUSES =
            EnumSet.of(DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT);

    /**
     * The delivery state machine: for each status, the statuses it is allowed to move to.
     * CREATED -> ASSIGNED happens only through assignDelivery, because it needs a vehicle;
     * the status endpoint handles every other move.
     */
    private static final Map<DeliveryStatus, Set<DeliveryStatus>> TRANSITIONS = Map.of(
            DeliveryStatus.CREATED,    EnumSet.of(DeliveryStatus.ASSIGNED, DeliveryStatus.CANCELLED),
            DeliveryStatus.ASSIGNED,   EnumSet.of(DeliveryStatus.PICKED_UP, DeliveryStatus.CANCELLED),
            DeliveryStatus.PICKED_UP,  EnumSet.of(DeliveryStatus.IN_TRANSIT),
            DeliveryStatus.IN_TRANSIT, EnumSet.of(DeliveryStatus.DELIVERED),
            DeliveryStatus.DELIVERED,  EnumSet.noneOf(DeliveryStatus.class),   // finished: no moves
            DeliveryStatus.CANCELLED,  EnumSet.noneOf(DeliveryStatus.class)    // finished: no moves
    );

    // Spring injects these through the Lombok constructor
    private final DeliveryRepository deliveryRepository;
    private final CustomerRepository customerRepository;
    private final VehicleRepository vehicleRepository;
    private final DeliveryMapper deliveryMapper;
    private final ProximitySearch proximitySearch;
    private final FleetProperties properties;

    /** One page of deliveries, converted to response DTOs. */
    public Page<DeliveryResponseDto> getDeliveries(Pageable pageable) {
        Page<Delivery> page = deliveryRepository.findAll(pageable);
        // Page.map converts every Delivery on the page with deliveryMapper.toDto
        return page.map(deliveryMapper::toDto);
    }

    @Transactional
    public DeliveryResponseDto createDelivery(DeliveryRequestDto requestDto) {
        // orElseThrow: return the customer if found, otherwise throw a 404 error
        Customer customer = customerRepository.findById(requestDto.getCustomerId())
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + requestDto.getCustomerId()));

        Delivery delivery = new Delivery();
        delivery.setCustomer(customer);
        delivery.setStatus(DeliveryStatus.CREATED);

        // Pickup point: use the one in the request, otherwise the customer's saved pickup address
        if (requestDto.getPickupLatitude() != null) {
            delivery.setPickupLatitude(requestDto.getPickupLatitude());
            delivery.setPickupLongitude(requestDto.getPickupLongitude());
        } else {
            delivery.setPickupLatitude(customer.getPickupLatitude());
            delivery.setPickupLongitude(customer.getPickupLongitude());
        }

        // Drop point: same rule as the pickup point
        if (requestDto.getDropLatitude() != null) {
            delivery.setDropLatitude(requestDto.getDropLatitude());
            delivery.setDropLongitude(requestDto.getDropLongitude());
        } else {
            delivery.setDropLatitude(customer.getDropLatitude());
            delivery.setDropLongitude(customer.getDropLongitude());
        }

        Delivery saved = deliveryRepository.save(delivery);
        log.info("Created delivery id={} for customer id={}", saved.getId(), customer.getId());
        return deliveryMapper.toDto(saved);
    }

    public DeliveryResponseDto getDelivery(Long id) {
        Delivery delivery = findDeliveryById(id);
        return deliveryMapper.toDto(delivery);
    }

    /**
     * Assigns a vehicle (and its driver) to a delivery.
     * Rules: delivery is CREATED; vehicle is AVAILABLE; vehicle has an ACTIVE driver; vehicle is
     * not on another active delivery. Afterwards: delivery ASSIGNED, vehicle BUSY.
     * Both rows are locked (delivery first, then vehicle, always in that order to avoid deadlocks).
     */
    @Transactional
    public DeliveryResponseDto assignDelivery(Long deliveryId, Long vehicleId) {
        // Lock order: delivery first, then vehicle
        Delivery delivery = lockDelivery(deliveryId);
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(vehicleId)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + vehicleId));

        assign(delivery, vehicle);
        return deliveryMapper.toDto(delivery);
    }

    /**
     * Assigns the nearest vehicle that can take the job: AVAILABLE, with an ACTIVE driver and a
     * location ping newer than fleettrack.location-max-age, within radiusKm of the pickup point.
     * <p>
     * Candidates come from an unlocked read, so by the time we lock the nearest one another
     * dispatcher may have taken it. We therefore lock candidates one by one, nearest first, and
     * re-check each under the lock; the first that still qualifies wins.
     */
    @Transactional
    public AutoAssignmentResponseDto autoAssign(Long deliveryId, Double radiusKm) {
        // Use the radius from the request, or the default from application.yml
        double radius = properties.autoAssignRadiusKm();
        if (radiusKm != null) {
            radius = radiusKm;
        }

        Delivery delivery = lockDelivery(deliveryId);
        if (delivery.getStatus() != DeliveryStatus.CREATED) {
            throw new ConflictException("Delivery must be in CREATED state to assign, current state: " + delivery.getStatus());
        }
        if (delivery.getPickupLatitude() == null || delivery.getPickupLongitude() == null) {
            throw new BadRequestException("Delivery has no pickup location to search from");
        }

        // Vehicles near the pickup point, nearest first (only ones with an active driver)
        List<ProximitySearch.Candidate> candidates = proximitySearch.nearestAvailable(
                delivery.getPickupLatitude(), delivery.getPickupLongitude(), radius, true);

        for (ProximitySearch.Candidate candidate : candidates) {
            // Lock this vehicle, then check again: another dispatcher may have just taken it
            Vehicle vehicle = vehicleRepository.findByIdForUpdate(candidate.vehicle().id()).orElse(null);
            if (vehicle == null || !isAssignable(vehicle)) {
                continue; // not usable any more, try the next nearest vehicle
            }

            assign(delivery, vehicle);
            double distanceKm = HaversineCalculator.roundToTwoDecimals(candidate.distanceKm());
            log.info("Auto-assigned vehicle id={} to delivery id={} at {} km", vehicle.getId(), deliveryId, distanceKm);
            DeliveryResponseDto deliveryDto = deliveryMapper.toDto(delivery);
            return new AutoAssignmentResponseDto(deliveryDto, distanceKm);
        }

        // The loop ended without returning, so no candidate could take the job
        throw new ConflictException("No available vehicle with an active driver within " + radius + " km of the pickup");
    }

    /** True if the vehicle is free and has an active driver. */
    private static boolean isAssignable(Vehicle vehicle) {
        if (vehicle.getStatus() != VehicleStatus.AVAILABLE) {
            return false;
        }
        if (vehicle.getDriver() == null) {
            return false;
        }
        return vehicle.getDriver().getStatus() == DriverStatus.ACTIVE;
    }

    /** Shared by manual and automatic assignment. Caller must hold locks on both rows. */
    void assign(Delivery delivery, Vehicle vehicle) {
        // Check every rule first; each failed rule gives a 409 Conflict with a clear message
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
        // Extra safety: the status check above should already guarantee this
        if (deliveryRepository.existsByVehicleIdAndStatusIn(vehicle.getId(), ACTIVE_STATUSES)) {
            throw new ConflictException("Vehicle is already assigned to another active delivery");
        }

        // All rules passed: link the delivery to the vehicle and its driver
        delivery.setVehicle(vehicle);
        delivery.setDriver(vehicle.getDriver());
        delivery.setStatus(DeliveryStatus.ASSIGNED);
        vehicle.setStatus(VehicleStatus.BUSY);
        // No save() needed: JPA writes changes to loaded entities when the transaction commits
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
        // ASSIGNED needs a vehicle, so it has its own endpoint
        if (newStatus == DeliveryStatus.ASSIGNED) {
            throw new BadRequestException("Use PATCH /api/deliveries/{id}/assign to assign a vehicle");
        }

        Delivery delivery = lockDelivery(id);
        DeliveryStatus oldStatus = delivery.getStatus();
        if (!isValidTransition(oldStatus, newStatus)) {
            throw new ConflictException("Invalid state transition from " + oldStatus + " to " + newStatus);
        }
        delivery.setStatus(newStatus);

        // A finished delivery gives its vehicle back
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

    /** True if the state machine allows moving from one status to the other. */
    static boolean isValidTransition(DeliveryStatus from, DeliveryStatus to) {
        Set<DeliveryStatus> allowedNext = TRANSITIONS.get(from);
        if (allowedNext == null) {
            return false; // unknown status: nothing is allowed
        }
        return allowedNext.contains(to);
    }

    /** DELIVERED and CANCELLED are final: the delivery can never change again. */
    static boolean isTerminal(DeliveryStatus status) {
        return status == DeliveryStatus.DELIVERED || status == DeliveryStatus.CANCELLED;
    }

    /** Normal read, no lock. */
    private Delivery findDeliveryById(Long id) {
        return deliveryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + id));
    }

    /** Read with SELECT ... FOR UPDATE, so no other transaction can change this row until we finish. */
    Delivery lockDelivery(Long id) {
        return deliveryRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + id));
    }
}
