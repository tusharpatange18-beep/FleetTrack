package com.fleettrack.service;

import com.fleettrack.config.FleetProperties;
import com.fleettrack.entity.enums.DriverStatus;
import com.fleettrack.entity.enums.VehicleStatus;
import com.fleettrack.repository.VehicleLocationView;
import com.fleettrack.repository.VehicleRepository;
import com.fleettrack.util.HaversineCalculator;
import com.fleettrack.util.HaversineCalculator.BoundingBox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Two-step proximity search shared by "nearby vehicles" and auto-assignment:
 * <ol>
 *   <li>SQL prefilter: AVAILABLE vehicles with a fresh ping inside the bounding box of the circle
 *       (indexed range scan, so the database never returns the whole fleet);</li>
 *   <li>exact Haversine distance in Java, drop the box corners outside the radius, sort.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class ProximitySearch {

    private final VehicleRepository vehicleRepository;
    private final FleetProperties properties;
    private final Clock clock;

    public record Candidate(VehicleLocationView vehicle, double distanceKm) {
    }

    /**
     * @param requireActiveDriver true for auto-assignment (only vehicles that can actually take
     *                            the job); false for the dispatcher's map view
     */
    public List<Candidate> nearestAvailable(double lat, double lon, double radiusKm, boolean requireActiveDriver) {
        BoundingBox box = HaversineCalculator.boundingBox(lat, lon, radiusKm);
        LocalDateTime freshSince = LocalDateTime.now(clock).minus(properties.locationMaxAge());

        List<VehicleLocationView> inBox = requireActiveDriver
                ? vehicleRepository.findWithDriverInBox(VehicleStatus.AVAILABLE, DriverStatus.ACTIVE,
                        box.minLat(), box.maxLat(), box.minLon(), box.maxLon(), freshSince)
                : vehicleRepository.findInBox(VehicleStatus.AVAILABLE,
                        box.minLat(), box.maxLat(), box.minLon(), box.maxLon(), freshSince);

        return inBox.stream()
                .map(v -> new Candidate(v, HaversineCalculator.calculateDistance(lat, lon, v.latitude(), v.longitude())))
                .filter(c -> c.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(Candidate::distanceKm)
                        .thenComparing(c -> c.vehicle().id())) // deterministic tie-break
                .toList();
    }
}
