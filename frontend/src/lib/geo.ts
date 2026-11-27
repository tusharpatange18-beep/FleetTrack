import type { LatLng } from '../api/types';

const EARTH_RADIUS_KM = 6371;

/** Degrees -> radians (Math.sin and Math.cos work in radians). */
function toRad(degrees: number): number {
  return (degrees * Math.PI) / 180;
}

/** Great-circle distance in km (same Haversine formula as the backend). */
export function haversineKm(a: LatLng, b: LatLng): number {
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);

  const sinHalfDLat = Math.sin(dLat / 2);
  const sinHalfDLng = Math.sin(dLng / 2);
  const h = sinHalfDLat * sinHalfDLat
    + Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * sinHalfDLng * sinHalfDLng;

  return 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

/**
 * Moves from `from` towards `to` by at most `stepKm`. Returns `to` exactly when it is within
 * one step, so a simulated vehicle always lands on its target. A straight line is fine
 * at city scale.
 */
export function stepToward(from: LatLng, to: LatLng, stepKm: number): LatLng {
  const distance = haversineKm(from, to);

  // Close enough: jump straight onto the target (a copy, so callers can't change `to`)
  if (distance <= stepKm || distance === 0) {
    return { lat: to.lat, lng: to.lng };
  }

  // Otherwise move this fraction of the way, e.g. 0.15 km of a 1.5 km trip = 0.1
  const fraction = stepKm / distance;
  return {
    lat: from.lat + (to.lat - from.lat) * fraction,
    lng: from.lng + (to.lng - from.lng) * fraction,
  };
}

/** Matches the backend's fleettrack.location-max-age default (15 minutes). */
export const LOCATION_MAX_AGE_MS = 15 * 60 * 1000;

/** True if the vehicle's last location is missing or older than 15 minutes. */
export function isStale(locationUpdatedAt: string | null, now = Date.now()): boolean {
  if (!locationUpdatedAt) {
    return true;
  }
  // The backend sends LocalDateTime without a zone; the browser reads it as local time,
  // which matches when backend and browser run on the same machine.
  const ageMs = now - new Date(locationUpdatedAt).getTime();
  return ageMs > LOCATION_MAX_AGE_MS;
}

/** Builds a LatLng, or null if either number is missing. */
export function toPoint(lat: number | null, lng: number | null): LatLng | null {
  if (lat == null || lng == null) {
    return null;
  }
  return { lat: lat, lng: lng };
}

/** "18.5204, 73.8567" for showing a point on screen, or a dash if it is missing. */
export function formatPoint(lat: number | null, lng: number | null): string {
  if (lat == null || lng == null) {
    return '—';
  }
  return lat.toFixed(4) + ', ' + lng.toFixed(4);
}
