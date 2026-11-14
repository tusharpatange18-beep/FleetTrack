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

    // ---- bounding box: it must never exclude a point that is inside the circle ----

    @org.junit.jupiter.params.ParameterizedTest(name = "centre ({0}, {1}), r={2} km")
    @org.junit.jupiter.params.provider.CsvSource({
            "19.0760, 72.8777, 1",     // Mumbai, short hop
            "19.0760, 72.8777, 50",
            "19.0760, 72.8777, 500",
            "60.0, 10.0, 500",         // high latitude: longitude degrees are short
            "-33.87, 151.21, 200",     // southern hemisphere
            "0.0, 179.9, 100",         // crosses the 180th meridian
            "89.9, 0.0, 50"            // next to the pole
    })
    void boundingBoxContainsEveryPointOnTheCircle(double lat, double lon, double radiusKm) {
        HaversineCalculator.BoundingBox box = HaversineCalculator.boundingBox(lat, lon, radiusKm);
        for (int bearing = 0; bearing < 360; bearing += 15) {
            double[] p = destination(lat, lon, radiusKm * 0.999, bearing);
            assertTrue(box.contains(p[0], p[1]),
                    "point at bearing " + bearing + " (" + p[0] + ", " + p[1] + ") is outside " + box);
        }
    }

    @Test
    void boundingBoxIsTightAwayFromPolesAndMeridian() {
        HaversineCalculator.BoundingBox box = HaversineCalculator.boundingBox(19.0760, 72.8777, 10);
        // 10 km is about 0.09 degrees of latitude; the box should not be wildly larger
        assertTrue(box.maxLat() - box.minLat() < 0.2, box.toString());
        assertTrue(box.maxLon() - box.minLon() < 0.2, box.toString());
        // A point well outside the radius is excluded
        assertFalse(box.contains(19.0760, 73.5));
    }

    @Test
    void boundingBoxAcrossTheMeridian_fallsBackToAllLongitudes() {
        HaversineCalculator.BoundingBox box = HaversineCalculator.boundingBox(0.0, 179.9, 100);
        assertEquals(-180.0, box.minLon());
        assertEquals(180.0, box.maxLon());
    }

    /** Point reached by travelling distanceKm from (lat, lon) on the given bearing (spherical Earth). */
    private static double[] destination(double lat, double lon, double distanceKm, double bearingDeg) {
        double d = distanceKm / 6371.0;
        double b = Math.toRadians(bearingDeg);
        double phi1 = Math.toRadians(lat);
        double lambda1 = Math.toRadians(lon);
        double phi2 = Math.asin(Math.sin(phi1) * Math.cos(d) + Math.cos(phi1) * Math.sin(d) * Math.cos(b));
        double lambda2 = lambda1 + Math.atan2(Math.sin(b) * Math.sin(d) * Math.cos(phi1),
                Math.cos(d) - Math.sin(phi1) * Math.sin(phi2));
        double lon2 = ((Math.toDegrees(lambda2) + 540) % 360) - 180; // normalise to [-180, 180)
        return new double[]{Math.toDegrees(phi2), lon2};
    }
}
