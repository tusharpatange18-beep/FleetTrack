package com.fleettrack.service;

import com.fleettrack.dto.response.DashboardSummaryDto;
import com.fleettrack.entity.enums.DeliveryStatus;
import com.fleettrack.entity.enums.DriverStatus;
import com.fleettrack.entity.enums.VehicleStatus;
import com.fleettrack.repository.DeliveryRepository;
import com.fleettrack.repository.DriverRepository;
import com.fleettrack.repository.StatusCount;
import com.fleettrack.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Numbers for the dispatcher's home screen. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final VehicleRepository vehicleRepository;
    private final DeliveryRepository deliveryRepository;
    private final DriverRepository driverRepository;

    /** Three GROUP BY / COUNT queries; no rows are loaded into memory. */
    public DashboardSummaryDto summary() {
        // How many vehicles and deliveries are in each status
        Map<String, Long> vehicles = zeroFilled(VehicleStatus.values(), vehicleRepository.countGroupedByStatus());
        Map<String, Long> deliveries = zeroFilled(DeliveryStatus.values(), deliveryRepository.countGroupedByStatus());

        // Active deliveries = ASSIGNED + PICKED_UP + IN_TRANSIT
        long activeDeliveries = 0;
        for (DeliveryStatus status : DeliveryService.ACTIVE_STATUSES) {
            activeDeliveries = activeDeliveries + deliveries.get(status.name());
        }

        long activeDrivers = driverRepository.countByStatus(DriverStatus.ACTIVE);
        return new DashboardSummaryDto(vehicles, deliveries, activeDeliveries, activeDrivers);
    }

    /**
     * Builds a map "status name -> count" that contains every status, with 0 for statuses the
     * database query did not return (a GROUP BY only returns statuses that have rows).
     */
    private static Map<String, Long> zeroFilled(Enum<?>[] allStatuses, List<StatusCount> counts) {
        // LinkedHashMap keeps the keys in the order we add them (the enum's order)
        Map<String, Long> result = new LinkedHashMap<>();

        // Start every status at 0
        for (Enum<?> status : allStatuses) {
            result.put(status.name(), 0L);
        }
        // Overwrite with the real counts from the database
        for (StatusCount row : counts) {
            result.put(row.status().name(), row.count());
        }
        return result;
    }
}
