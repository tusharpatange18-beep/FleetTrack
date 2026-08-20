package com.fleettrack.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HaversineCalculatorTest {

    @Test
    void sameCoordinatesShouldReturnZero() {
        double dist = HaversineCalculator.calculateDistance(19.0760, 72.8777, 19.0760, 72.8777);
        assertEquals(0.0, dist, 0.001, "Distance between same coordinates should be 0");
    }

    @Test
    void mumbaiToPuneApprox118km() {
        // Mumbai (19.0760, 72.8777) to Pune (18.5204, 73.8567) ≈ 118 km
        double dist = HaversineCalculator.calculateDistance(19.0760, 72.8777, 18.5204, 73.8567);
        assertTrue(dist > 110 && dist < 125,
                "Mumbai to Pune should be approx 118 km, got: " + dist);
    }

    @Test
    void mumbaiBandraToAndheriApprox8km() {
        // Bandra (19.0596, 72.8295) to Andheri (19.1197, 72.8464) ≈ 6.9 km
        double dist = HaversineCalculator.calculateDistance(19.0596, 72.8295, 19.1197, 72.8464);
        assertTrue(dist > 5 && dist < 10,
                "Bandra to Andheri should be approx 7 km, got: " + dist);
    }

    @Test
    void distanceIsSymmetric() {
        double dist1 = HaversineCalculator.calculateDistance(19.0760, 72.8777, 18.5204, 73.8567);
        double dist2 = HaversineCalculator.calculateDistance(18.5204, 73.8567, 19.0760, 72.8777);
        assertEquals(dist1, dist2, 0.001, "Distance should be symmetric");
    }

    @Test
    void northAndSouthPoleDistance() {
        // Distance from north to south pole should be ~20,015 km (half circumference)
        double dist = HaversineCalculator.calculateDistance(90.0, 0.0, -90.0, 0.0);
        assertTrue(dist > 19000 && dist < 21000,
                "Pole-to-pole distance should be ~20015 km, got: " + dist);
    }
}
