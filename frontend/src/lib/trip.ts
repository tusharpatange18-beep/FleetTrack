import type { Delivery, DeliveryStatus, LatLng } from '../api/types';
import { stepToward, toPoint } from './geo';

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
 * One step of the simulated trip (a "pure" function: same input, same output, no side effects):
 * ASSIGNED   -> drive to pickup; on arrival mark PICKED_UP and IN_TRANSIT
 * IN_TRANSIT -> drive to drop;   on arrival mark DELIVERED
 * PICKED_UP  -> (picked up but not moving yet) mark IN_TRANSIT
 */
export function tripTick(delivery: Delivery, position: LatLng, stepKm: number): TripTick {
  const pickup = toPoint(delivery.pickupLatitude, delivery.pickupLongitude);
  const drop = toPoint(delivery.dropLatitude, delivery.dropLongitude);

  if (delivery.status === 'ASSIGNED') {
    if (!pickup) {
      return idle('No pickup location');
    }
    const next = stepToward(position, pickup, stepKm);
    if (samePoint(next, pickup)) {
      // Arrived at the pickup: collect the parcel and set off straight away
      return { position: next, statusChanges: ['PICKED_UP', 'IN_TRANSIT'], phase: 'Picked up, heading to drop', done: false };
    }
    return { position: next, statusChanges: [], phase: 'Driving to pickup', done: false };
  }

  if (delivery.status === 'PICKED_UP') {
    return { position: null, statusChanges: ['IN_TRANSIT'], phase: 'Starting trip', done: false };
  }

  if (delivery.status === 'IN_TRANSIT') {
    if (!drop) {
      return idle('No drop location');
    }
    const next = stepToward(position, drop, stepKm);
    if (samePoint(next, drop)) {
      // Arrived at the drop: the trip is over
      return { position: next, statusChanges: ['DELIVERED'], phase: 'Delivered', done: true };
    }
    return { position: next, statusChanges: [], phase: 'Driving to drop', done: false };
  }

  // CREATED, DELIVERED or CANCELLED: nothing to drive
  return { position: null, statusChanges: [], phase: 'Trip finished', done: true };
}

/** A tick that does nothing except show a message. */
function idle(phase: string): TripTick {
  return { position: null, statusChanges: [], phase: phase, done: false };
}

function samePoint(a: LatLng, b: LatLng): boolean {
  return a.lat === b.lat && a.lng === b.lng;
}
