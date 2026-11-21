import { useEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import type { Delivery, LatLng } from '../api/types';
import { ACTIVE_DELIVERY_STATUSES } from '../api/types';
import { FleetMap } from '../components/FleetMap';
import { Header, StatusChip } from '../components/Header';
import { haversineKm, isStale } from '../lib/geo';
import { tripTick } from '../lib/trip';
import { usePolling } from '../lib/usePolling';

const SPEEDS = [
  { label: 'Normal (fast-forward)', stepKm: 0.15 },
  { label: 'Fast', stepKm: 0.4 },
  { label: 'Very fast', stepKm: 1 },
];

/**
 * A phone-style driver screen. The backend has no driver login, so the person picks which
 * vehicle they are driving. "Start trip" simulates GPS: every second it moves the vehicle
 * towards the pickup and then the drop, sending real location updates and status changes.
 */
export function DriverPage() {
  const live = usePolling(async () => {
    const [vehicles, deliveries, drivers] = await Promise.all([api.vehicles(), api.deliveries(), api.drivers()]);
    return { vehicles, deliveries, drivers };
  }, 2000);
  const reload = live.reload;

  const [vehicleId, setVehicleId] = useState<number | null>(null);
  const [running, setRunning] = useState(false);
  const [speed, setSpeed] = useState(0);
  const [phase, setPhase] = useState<string>('');
  const [error, setError] = useState<string | null>(null);

  const drivable = (live.data?.vehicles ?? []).filter(v => v.driverId != null && v.status !== 'INACTIVE');
  const vehicle = drivable.find(v => v.id === vehicleId) ?? null;
  const job: Delivery | null = vehicle
    ? (live.data?.deliveries ?? []).find(d => d.vehicleId === vehicle.id && ACTIVE_DELIVERY_STATUSES.includes(d.status)) ?? null
    : null;
  const driver = vehicle ? live.data?.drivers.find(d => d.id === vehicle.driverId) : undefined;
  const position: LatLng | null = vehicle?.currentLatitude != null && vehicle.currentLongitude != null
    ? { lat: vehicle.currentLatitude, lng: vehicle.currentLongitude } : null;

  // Pick the first drivable vehicle by default
  useEffect(() => {
    if (vehicleId == null && drivable.length > 0) setVehicleId(drivable[0].id);
  }, [vehicleId, drivable]);

  // Stop the simulation when the vehicle changes or the job ends
  useEffect(() => {
    if (!job) setRunning(false);
  }, [job]);

  // ---- trip simulation: one tick per second ----
  const latest = useRef({ job, position, vehicle });
  latest.current = { job, position, vehicle };
  const inFlight = useRef(false);

  useEffect(() => {
    if (!running) return;
    const timer = setInterval(async () => {
      const { job: j, position: p, vehicle: v } = latest.current;
      if (!j || !p || !v || inFlight.current) return;
      inFlight.current = true;
      try {
        const tick = tripTick(j, p, SPEEDS[speed].stepKm);
        setPhase(tick.phase);
        if (tick.position) await api.updateLocation(v.id, tick.position.lat, tick.position.lng);
        for (const status of tick.statusChanges) await api.setDeliveryStatus(j.id, status);
        if (tick.done) setRunning(false);
        await reload();
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
        setRunning(false);
      } finally {
        inFlight.current = false;
      }
    }, 1000);
    return () => clearInterval(timer);
  }, [running, speed, reload]);

  async function act(action: () => Promise<unknown>) {
    try {
      setError(null);
      await action();
      await reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  const target = job && (job.status === 'ASSIGNED' ? point(job.pickupLatitude, job.pickupLongitude)
    : point(job.dropLatitude, job.dropLongitude));
  const remainingKm = target && position ? haversineKm(position, target) : null;

  return (
    <div className="app-shell">
      <Header />
      <div className="driver-stage">
        <div className="phone">
          <div className="phone-top">
            <select value={vehicleId ?? ''} onChange={e => { setRunning(false); setVehicleId(Number(e.target.value)); }}>
              {drivable.length === 0 && <option value="">No vehicle has a driver yet</option>}
              {drivable.map(v => <option key={v.id} value={v.id}>{v.registrationNumber} ({v.type})</option>)}
            </select>
            {vehicle && <StatusChip status={vehicle.status} />}
          </div>

          <div className="phone-map">
            <FleetMap vehicles={vehicle ? [vehicle] : []} deliveries={job ? [job] : []}
                      selectedDeliveryId={job?.id} highlightVehicleId={vehicle?.id}
                      follow={running ? position : null}
                      onMapClick={p => !running && vehicle && act(() => api.updateLocation(vehicle.id, p.lat, p.lng))} />
          </div>

          <div className="phone-sheet">
            {driver && <div className="driver-name">👤 {driver.name}</div>}
            {error && <div className="alert">{error}</div>}
            {!vehicle && <p className="muted">Ask an admin to assign a driver to a vehicle.</p>}

            {vehicle && !position && <p className="muted">Tap the map to share your location.</p>}
            {vehicle && position && isStale(vehicle.locationUpdatedAt) && !job && (
              <p className="muted small">Your location is stale, so dispatch can't see you. Tap the map to update it.</p>
            )}

            {vehicle && !job && (
              <>
                <h3>{vehicle.status === 'AVAILABLE' ? 'Online · waiting for a job' : 'You are offline'}</h3>
                <p className="muted small">Tap the map to move. New jobs from dispatch appear here automatically.</p>
                {vehicle.status === 'AVAILABLE' && (
                  <button className="btn btn-block" onClick={() => act(() => api.setVehicleStatus(vehicle.id, 'OFFLINE'))}>Go offline</button>
                )}
                {vehicle.status === 'OFFLINE' && (
                  <button className="btn btn-primary btn-block" onClick={() => act(() => api.setVehicleStatus(vehicle.id, 'AVAILABLE'))}>Go online</button>
                )}
                {vehicle.status === 'BUSY' && (
                  <p className="muted small">Marked BUSY without an active job (old data). Dispatch can set it back to AVAILABLE.</p>
                )}
              </>
            )}

            {vehicle && job && (
              <div className="job">
                <div className="card-head">
                  <h3>Job #{job.id}</h3>
                  <StatusChip status={job.status} />
                </div>
                <p className="job-phase">
                  {running ? phase || 'Starting…' : job.status === 'ASSIGNED' ? 'Go to the pickup' : 'Deliver to the drop point'}
                  {remainingKm != null && <span className="muted"> · {remainingKm.toFixed(2)} km to go</span>}
                </p>
                <div className="row">
                  <button className={`btn btn-block ${running ? '' : 'btn-primary'}`} disabled={!position}
                          onClick={() => setRunning(r => !r)}>
                    {running ? 'Pause' : 'Start trip (simulate GPS)'}
                  </button>
                  <select value={speed} onChange={e => setSpeed(Number(e.target.value))} aria-label="Simulation speed">
                    {SPEEDS.map((s, i) => <option key={s.label} value={i}>{s.label}</option>)}
                  </select>
                </div>
                <div className="row manual">
                  {job.status === 'ASSIGNED' && (
                    <button className="btn btn-block" onClick={() => act(() => api.setDeliveryStatus(job.id, 'PICKED_UP'))}>Picked up</button>
                  )}
                  {job.status === 'PICKED_UP' && (
                    <button className="btn btn-block" onClick={() => act(() => api.setDeliveryStatus(job.id, 'IN_TRANSIT'))}>Start delivery</button>
                  )}
                  {job.status === 'IN_TRANSIT' && (
                    <button className="btn btn-block" onClick={() => act(() => api.setDeliveryStatus(job.id, 'DELIVERED'))}>Delivered</button>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>
        <p className="muted small stage-note">
          Tip: open <b>Dispatch</b> in another tab to watch this vehicle move on the fleet map.
        </p>
      </div>
    </div>
  );
}

function point(lat: number | null, lng: number | null): LatLng | null {
  return lat == null || lng == null ? null : { lat, lng };
}
