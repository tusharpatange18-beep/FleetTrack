import type { LatLng } from '../api/types';

const EARTH_RADIUS_KM = 6371;
const toRad = (deg: number) => (deg * Math.PI) / 180;

/** Great-circle distance in km (same formula as the backend). */
export function haversineKm(a: LatLng, b: LatLng): number {
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

/**
 * Moves from `from` towards `to` by at most `stepKm`. Returns `to` exactly when it is within
 * one step, so a simulated vehicle always lands on its target. Linear interpolation is fine
 * at city scale.
 */
export function stepToward(from: LatLng, to: LatLng, stepKm: number): LatLng {
  const distance = haversineKm(from, to);
  if (distance <= stepKm || distance === 0) return { ...to };
  const f = stepKm / distance;
  return { lat: from.lat + (to.lat - from.lat) * f, lng: from.lng + (to.lng - from.lng) * f };
}

/** Matches the backend's fleettrack.location-max-age default. */
export const LOCATION_MAX_AGE_MS = 15 * 60 * 1000;

export function isStale(locationUpdatedAt: string | null, now = Date.now()): boolean {
  if (!locationUpdatedAt) return true;
  // The backend sends LocalDateTime without a zone; the browser reads it as local time,
  // which matches when backend and browser run on the same machine.
  return now - new Date(locationUpdatedAt).getTime() > LOCATION_MAX_AGE_MS;
}
