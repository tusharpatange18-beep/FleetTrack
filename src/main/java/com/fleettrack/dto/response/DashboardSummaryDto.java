package com.fleettrack.dto.response;

import java.util.Map;

/**
 * Fleet overview for a dispatcher's home screen. Every status appears in the maps, with 0 when
 * there are none, so a client can render a fixed layout.
 */
public record DashboardSummaryDto(
        Map<String, Long> vehiclesByStatus,
        Map<String, Long> deliveriesByStatus,
        long activeDeliveries,
        long activeDrivers) {
}
