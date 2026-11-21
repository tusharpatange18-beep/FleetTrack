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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final VehicleRepository vehicleRepository;
    private final DeliveryRepository deliveryRepository;
    private final DriverRepository driverRepository;

    /** Three GROUP BY / COUNT queries; no rows are loaded into memory. */
    public DashboardSummaryDto summary() {
        Map<String, Long> vehicles = zeroFilled(VehicleStatus.values(), vehicleRepository.countGroupedByStatus());
        Map<String, Long> deliveries = zeroFilled(DeliveryStatus.values(), deliveryRepository.countGroupedByStatus());
        long active = DeliveryService.ACTIVE_STATUSES.stream().mapToLong(s -> deliveries.get(s.name())).sum();
        return new DashboardSummaryDto(vehicles, deliveries, active, driverRepository.countByStatus(DriverStatus.ACTIVE));
    }

    private static Map<String, Long> zeroFilled(Enum<?>[] allStatuses, List<StatusCount> counts) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Enum<?> status : allStatuses) {
            result.put(status.name(), 0L);
        }
        counts.forEach(c -> result.put(c.status().name(), c.count()));
        return result;
    }
}
