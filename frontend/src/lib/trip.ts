import type { Delivery, DeliveryStatus, LatLng } from '../api/types';
import { stepToward } from './geo';

/** What the driver simulator should do on one tick. */
export interface TripTick {
  /** New position to report, or null to stay put. */
  position: LatLng | null;
  /** Status updates to send to the backend, in order. */
  statusChanges: DeliveryStatus[];
  /** Human-readable phase for the driver screen. */
  phase: string;
  done: boolean;
}

/**
 * Pure step function for the simulated trip:
 * ASSIGNED   -> drive to pickup; on arrival mark PICKED_UP and IN_TRANSIT
 * IN_TRANSIT -> drive to drop;   on arrival mark DELIVERED
 * PICKED_UP  -> (picked up but not moving yet) mark IN_TRANSIT
 */
export function tripTick(delivery: Delivery, position: LatLng, stepKm: number): TripTick {
  const pickup = point(delivery.pickupLatitude, delivery.pickupLongitude);
  const drop = point(delivery.dropLatitude, delivery.dropLongitude);

  switch (delivery.status) {
    case 'ASSIGNED': {
      if (!pickup) return idle('No pickup location');
      const next = stepToward(position, pickup, stepKm);
      const arrived = same(next, pickup);
      return {
        position: next,
        statusChanges: arrived ? ['PICKED_UP', 'IN_TRANSIT'] : [],
        phase: arrived ? 'Picked up, heading to drop' : 'Driving to pickup',
        done: false,
      };
    }
    case 'PICKED_UP':
      return { position: null, statusChanges: ['IN_TRANSIT'], phase: 'Starting trip', done: false };
    case 'IN_TRANSIT': {
      if (!drop) return idle('No drop location');
      const next = stepToward(position, drop, stepKm);
      const arrived = same(next, drop);
      return {
        position: next,
        statusChanges: arrived ? ['DELIVERED'] : [],
        phase: arrived ? 'Delivered' : 'Driving to drop',
        done: arrived,
      };
    }
    default:
      return { ...idle('Trip finished'), done: true };
  }
}

function idle(phase: string): TripTick {
  return { position: null, statusChanges: [], phase, done: false };
}

function point(lat: number | null, lng: number | null): LatLng | null {
  return lat == null || lng == null ? null : { lat, lng };
}

function same(a: LatLng, b: LatLng) {
  return a.lat === b.lat && a.lng === b.lng;
}
