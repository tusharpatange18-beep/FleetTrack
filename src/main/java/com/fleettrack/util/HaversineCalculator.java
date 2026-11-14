package com.fleettrack.util;

/**
 * Great-circle distance on a spherical Earth, plus the bounding box used to prefilter
 * candidates in SQL before the exact distance is computed in Java.
 */
public final class HaversineCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0;
    /** Length of one degree of latitude (and of longitude at the equator), in km. */
    private static final double KM_PER_DEGREE = Math.PI * EARTH_RADIUS_KM / 180.0;

    private HaversineCalculator() {
    }

    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(rLat1) * Math.cos(rLat2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Smallest lat/lon rectangle that contains every point within radiusKm of the centre.
     * Cheap to test in SQL with BETWEEN and an index; anything inside it still needs the exact
     * Haversine check because the box's corners lie outside the circle.
     * Near the poles, or when the box would cross the 180th meridian, longitude is left unbounded
     * so that no vehicle is ever wrongly excluded.
     */
    public static BoundingBox boundingBox(double lat, double lon, double radiusKm) {
        double latDelta = radiusKm / KM_PER_DEGREE;
        double minLat = Math.max(-90.0, lat - latDelta);
        double maxLat = Math.min(90.0, lat + latDelta);

        // Longitude degrees shrink with cos(latitude); use the widest latitude in the box
        double widestLat = Math.max(Math.abs(minLat), Math.abs(maxLat));
        double cos = Math.cos(Math.toRadians(widestLat));
        if (cos < 1e-6) {
            return new BoundingBox(minLat, maxLat, -180.0, 180.0);
        }
        double lonDelta = radiusKm / (KM_PER_DEGREE * cos);
        double minLon = lon - lonDelta;
        double maxLon = lon + lonDelta;
        if (lonDelta >= 180.0 || minLon < -180.0 || maxLon > 180.0) {
            return new BoundingBox(minLat, maxLat, -180.0, 180.0);
        }
        return new BoundingBox(minLat, maxLat, minLon, maxLon);
    }

    public record BoundingBox(double minLat, double maxLat, double minLon, double maxLon) {
        public boolean contains(double lat, double lon) {
            return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
        }
    }
}
