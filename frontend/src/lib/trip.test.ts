import { describe, expect, it } from 'vitest';
import type { Delivery } from '../api/types';
import { haversineKm, isStale, stepToward } from './geo';
import { tripTick } from './trip';

const PUNE = { lat: 18.5204, lng: 73.8567 };
const NEARBY = { lat: 18.53, lng: 73.85 };

function delivery(status: Delivery['status']): Delivery {
  return {
    id: 1, vehicleId: 7, driverId: 3, customerId: 1, status,
    pickupLatitude: NEARBY.lat, pickupLongitude: NEARBY.lng,
    dropLatitude: 18.6, dropLongitude: 73.8,
  };
}

describe('geo', () => {
  it('matches the backend Haversine distance', () => {
    expect(haversineKm(NEARBY, PUNE)).toBeCloseTo(1.28, 2);
  });

  it('stepToward moves exactly one step, then lands on the target', () => {
    const next = stepToward(PUNE, NEARBY, 0.5);
    expect(haversineKm(PUNE, next)).toBeCloseTo(0.5, 2);
    expect(stepToward(PUNE, NEARBY, 5)).toEqual(NEARBY);
  });

  it('treats missing or old timestamps as stale', () => {
    const now = new Date('2026-11-14T10:00:00').getTime();
    expect(isStale(null, now)).toBe(true);
    expect(isStale('2026-11-14T09:50:00', now)).toBe(false);
    expect(isStale('2026-11-14T09:40:00', now)).toBe(true);
  });
});

describe('tripTick', () => {
  it('drives towards the pickup while ASSIGNED', () => {
    const tick = tripTick(delivery('ASSIGNED'), PUNE, 0.2);
    expect(tick.statusChanges).toEqual([]);
    expect(haversineKm(tick.position!, NEARBY)).toBeLessThan(haversineKm(PUNE, NEARBY));
  });

  it('on reaching the pickup, marks PICKED_UP then IN_TRANSIT', () => {
    const tick = tripTick(delivery('ASSIGNED'), PUNE, 10);
    expect(tick.position).toEqual(NEARBY);
    expect(tick.statusChanges).toEqual(['PICKED_UP', 'IN_TRANSIT']);
    expect(tick.done).toBe(false);
  });

  it('a PICKED_UP job is started without moving', () => {
    const tick = tripTick(delivery('PICKED_UP'), NEARBY, 0.2);
    expect(tick.position).toBeNull();
    expect(tick.statusChanges).toEqual(['IN_TRANSIT']);
  });

  it('on reaching the drop, marks DELIVERED and finishes', () => {
    const tick = tripTick(delivery('IN_TRANSIT'), NEARBY, 100);
    expect(tick.statusChanges).toEqual(['DELIVERED']);
    expect(tick.done).toBe(true);
  });

  it('a whole trip ends with every status in order', () => {
    let job = delivery('ASSIGNED');
    let position = PUNE;
    const seen: string[] = [];
    for (let i = 0; i < 1000 && job.status !== 'DELIVERED'; i++) {
      const tick = tripTick(job, position, 0.15);
      if (tick.position) position = tick.position;
      for (const s of tick.statusChanges) {
        seen.push(s);
        job = { ...job, status: s };
      }
    }
    expect(seen).toEqual(['PICKED_UP', 'IN_TRANSIT', 'DELIVERED']);
    expect(position).toEqual({ lat: 18.6, lng: 73.8 });
  });
});
