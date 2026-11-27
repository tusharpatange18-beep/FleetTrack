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
import java.util.ArrayList;
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

    /** One search result: a vehicle and its distance from the search point. */
    public record Candidate(VehicleLocationView vehicle, double distanceKm) {
    }

    /**
     * @param requireActiveDriver true for auto-assignment (only vehicles that can actually take
     *                            the job); false for the dispatcher's map view
     * @return vehicles within radiusKm, nearest first
     */
    public List<Candidate> nearestAvailable(double lat, double lon, double radiusKm, boolean requireActiveDriver) {
        // Step 1: rectangle around the circle, and the oldest location ping we still trust
        BoundingBox box = HaversineCalculator.boundingBox(lat, lon, radiusKm);
        LocalDateTime freshSince = LocalDateTime.now(clock).minus(properties.locationMaxAge());

        // Step 2: ask the database for vehicles inside the rectangle
        List<VehicleLocationView> inBox;
        if (requireActiveDriver) {
            inBox = vehicleRepository.findWithDriverInBox(VehicleStatus.AVAILABLE, DriverStatus.ACTIVE,
                    box.minLat(), box.maxLat(), box.minLon(), box.maxLon(), freshSince);
        } else {
            inBox = vehicleRepository.findInBox(VehicleStatus.AVAILABLE,
                    box.minLat(), box.maxLat(), box.minLon(), box.maxLon(), freshSince);
        }

        // Step 3: exact distance for each one; keep only those really inside the circle
        List<Candidate> result = new ArrayList<>();
        for (VehicleLocationView vehicle : inBox) {
            double distanceKm = HaversineCalculator.calculateDistance(lat, lon, vehicle.latitude(), vehicle.longitude());
            if (distanceKm <= radiusKm) {
                result.add(new Candidate(vehicle, distanceKm));
            }
        }

        // Step 4: nearest first (compareCandidates below decides the order)
        result.sort(ProximitySearch::compareCandidates);
        return result;
    }

    /**
     * Sort order: smaller distance first. If two vehicles are at exactly the same distance,
     * the smaller vehicle id comes first, so the result is always in the same order.
     * Returns a negative number if a comes first, positive if b comes first.
     */
    private static int compareCandidates(Candidate a, Candidate b) {
        int byDistance = Double.compare(a.distanceKm(), b.distanceKm());
        if (byDistance != 0) {
            return byDistance;
        }
        return Long.compare(a.vehicle().id(), b.vehicle().id());
    }
}
