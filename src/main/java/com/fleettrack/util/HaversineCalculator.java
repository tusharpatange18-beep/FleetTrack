package com.fleettrack.util;

/**
 * Great-circle distance on a spherical Earth, plus the bounding box used to prefilter
 * candidates in SQL before the exact distance is computed in Java.
 */
public final class HaversineCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0;
    /** Length of one degree of latitude (and of longitude at the equator), in km. About 111 km. */
    private static final double KM_PER_DEGREE = Math.PI * EARTH_RADIUS_KM / 180.0;

    // Only static methods, so nobody should create an object of this class
    private HaversineCalculator() {
    }

    /**
     * Haversine formula: distance in km between two points given in degrees.
     * a = sin²(Δlat/2) + cos(lat1) · cos(lat2) · sin²(Δlon/2)
     * c = 2 · atan2(√a, √(1−a))
     * distance = R · c
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Java's Math.sin/cos work in radians, so convert the degrees first
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double sinHalfDLat = Math.sin(dLat / 2);
        double sinHalfDLon = Math.sin(dLon / 2);
        double a = sinHalfDLat * sinHalfDLat
                + sinHalfDLon * sinHalfDLon * Math.cos(rLat1) * Math.cos(rLat2);

        // c is the angle between the two points, seen from the centre of the Earth
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /** Rounds a distance to 2 decimal places, e.g. 3.14159 becomes 3.14. */
    public static double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Smallest lat/lon rectangle that contains every point within radiusKm of the centre.
     * Cheap to test in SQL with BETWEEN and an index; anything inside it still needs the exact
     * Haversine check because the box's corners lie outside the circle.
     * Near the poles, or when the box would cross the 180th meridian, longitude is left unbounded
     * so that no vehicle is ever wrongly excluded.
     */
    public static BoundingBox boundingBox(double lat, double lon, double radiusKm) {
        // How many degrees of latitude the radius covers; latitude stays between -90 and 90
        double latDelta = radiusKm / KM_PER_DEGREE;
        double minLat = Math.max(-90.0, lat - latDelta);
        double maxLat = Math.min(90.0, lat + latDelta);

        // Longitude degrees get shorter away from the equator (by cos(latitude)),
        // so use the latitude furthest from the equator to get the widest box
        double widestLat = Math.max(Math.abs(minLat), Math.abs(maxLat));
        double cos = Math.cos(Math.toRadians(widestLat));
        if (cos < 1e-6) {
            // At a pole every longitude is close by: allow all longitudes
            return new BoundingBox(minLat, maxLat, -180.0, 180.0);
        }

        double lonDelta = radiusKm / (KM_PER_DEGREE * cos);
        double minLon = lon - lonDelta;
        double maxLon = lon + lonDelta;
        if (lonDelta >= 180.0 || minLon < -180.0 || maxLon > 180.0) {
            // The box would wrap around the 180th meridian: allow all longitudes to stay safe
            return new BoundingBox(minLat, maxLat, -180.0, 180.0);
        }
        return new BoundingBox(minLat, maxLat, minLon, maxLon);
    }

    /** A rectangle of latitudes and longitudes. A record is a small class with fields, getters and equals for free. */
    public record BoundingBox(double minLat, double maxLat, double minLon, double maxLon) {

        /** True if the point lies inside the rectangle (edges included). */
        public boolean contains(double lat, double lon) {
            boolean latInside = lat >= minLat && lat <= maxLat;
            boolean lonInside = lon >= minLon && lon <= maxLon;
            return latInside && lonInside;
        }
    }
}
