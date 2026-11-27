import { useEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import type { Delivery, Driver, LatLng, Vehicle } from '../api/types';
import { ACTIVE_DELIVERY_STATUSES } from '../api/types';
import { FleetMap } from '../components/FleetMap';
import { Header, StatusChip } from '../components/Header';
import { errorMessage } from '../lib/errors';
import { haversineKm, isStale, toPoint } from '../lib/geo';
import { tripTick } from '../lib/trip';
import { usePolling } from '../lib/usePolling';

// How far the simulated vehicle moves per second
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
  // Live data, refreshed every 2 seconds
  const live = usePolling(async () => {
    const [vehicles, deliveries, drivers] = await Promise.all([api.vehicles(), api.deliveries(), api.drivers()]);
    return { vehicles, deliveries, drivers };
  }, 2000);
  const reload = live.reload;

  const [vehicleId, setVehicleId] = useState<number | null>(null);
  const [running, setRunning] = useState(false); // is the trip simulation running?
  const [speed, setSpeed] = useState(0);         // index into SPEEDS
  const [phase, setPhase] = useState<string>('');
  const [error, setError] = useState<string | null>(null);

  let allVehicles: Vehicle[] = [];
  let allDeliveries: Delivery[] = [];
  let allDrivers: Driver[] = [];
  if (live.data) {
    allVehicles = live.data.vehicles;
    allDeliveries = live.data.deliveries;
    allDrivers = live.data.drivers;
  }

  // Vehicles you can "drive": they have a driver and are not deleted
  const drivable: Vehicle[] = [];
  for (const v of allVehicles) {
    if (v.driverId != null && v.status !== 'INACTIVE') {
      drivable.push(v);
    }
  }

  // The vehicle picked in the drop-down
  let vehicle: Vehicle | null = null;
  for (const v of drivable) {
    if (v.id === vehicleId) {
      vehicle = v;
      break;
    }
  }

  // The vehicle's current job (an ASSIGNED, PICKED_UP or IN_TRANSIT delivery), its driver and position
  let job: Delivery | null = null;
  let driver: Driver | undefined = undefined;
  let position: LatLng | null = null;
  if (vehicle) {
    for (const d of allDeliveries) {
      if (d.vehicleId === vehicle.id && ACTIVE_DELIVERY_STATUSES.includes(d.status)) {
        job = d;
        break;
      }
    }
    for (const person of allDrivers) {
      if (person.id === vehicle.driverId) {
        driver = person;
        break;
      }
    }
    position = toPoint(vehicle.currentLatitude, vehicle.currentLongitude);
  }

  // Pick the first drivable vehicle by default
  useEffect(() => {
    if (vehicleId == null && drivable.length > 0) {
      setVehicleId(drivable[0].id);
    }
  }, [vehicleId, drivable]);

  // Stop the simulation when the vehicle changes or the job ends
  useEffect(() => {
    if (!job) {
      setRunning(false);
    }
  }, [job]);

  // ---- trip simulation: one tick per second ----

  // The timer below is created once, so it can't see new values of job/position/vehicle.
  // A ref is a box whose content we replace on every render; the timer always reads the newest.
  const latest = useRef({ job, position, vehicle });
  latest.current = { job, position, vehicle };
  // True while one tick's requests are still running, so ticks never overlap
  const inFlight = useRef(false);

  useEffect(() => {
    if (!running) {
      return;
    }

    const timer = setInterval(async () => {
      const currentJob = latest.current.job;
      const currentPosition = latest.current.position;
      const currentVehicle = latest.current.vehicle;
      if (!currentJob || !currentPosition || !currentVehicle || inFlight.current) {
        return;
      }

      inFlight.current = true;
      try {
        // Work out the next step (pure function in lib/trip.ts), then send it to the backend
        const tick = tripTick(currentJob, currentPosition, SPEEDS[speed].stepKm);
        setPhase(tick.phase);
        if (tick.position) {
          await api.updateLocation(currentVehicle.id, tick.position.lat, tick.position.lng);
        }
        // Status changes must be sent in order, one after the other
        for (const status of tick.statusChanges) {
          await api.setDeliveryStatus(currentJob.id, status);
        }
        if (tick.done) {
          setRunning(false);
        }
        await reload();
      } catch (e) {
        setError(errorMessage(e));
        setRunning(false);
      } finally {
        inFlight.current = false;
      }
    }, 1000);

    // Stop the timer when the trip is paused or the page closes
    return () => clearInterval(timer);
  }, [running, speed, reload]);

  // Runs one button action, shows any error, then refreshes the data
  async function act(action: () => Promise<unknown>) {
    try {
      setError(null);
      await action();
      await reload();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  // Distance left to the current target: the pickup first, then the drop
  let remainingKm: number | null = null;
  if (job && position) {
    let target = toPoint(job.dropLatitude, job.dropLongitude);
    if (job.status === 'ASSIGNED') {
      target = toPoint(job.pickupLatitude, job.pickupLongitude);
    }
    if (target) {
      remainingKm = haversineKm(position, target);
    }
  }

  // The line under the job title
  let phaseText = 'Deliver to the drop point';
  if (running) {
    phaseText = phase || 'Starting…';
  } else if (job && job.status === 'ASSIGNED') {
    phaseText = 'Go to the pickup';
  }

  function chooseVehicle(id: number) {
    setRunning(false); // never keep simulating a different vehicle
    setVehicleId(id);
  }

  // Tapping the map moves the vehicle there (only while not simulating)
  function onMapClick(point: LatLng) {
    if (!running && vehicle) {
      const id = vehicle.id;
      act(() => api.updateLocation(id, point.lat, point.lng));
    }
  }

  let mapVehicles: Vehicle[] = [];
  if (vehicle) {
    mapVehicles = [vehicle];
  }
  let mapDeliveries: Delivery[] = [];
  if (job) {
    mapDeliveries = [job];
  }
  let followPoint: LatLng | null = null;
  if (running) {
    followPoint = position; // keep the moving vehicle in the middle of the map
  }

  // The drop-down needs a value even before a vehicle is chosen ('' = nothing chosen)
  let selectValue: number | string = '';
  if (vehicleId != null) {
    selectValue = vehicleId;
  }

  let tripButtonClass = 'btn btn-block btn-primary';
  let tripButtonText = 'Start trip (simulate GPS)';
  if (running) {
    tripButtonClass = 'btn btn-block ';
    tripButtonText = 'Pause';
  }

  return (
    <div className="app-shell">
      <Header />
      <div className="driver-stage">
        <div className="phone">
          <div className="phone-top">
            <select value={selectValue} onChange={e => chooseVehicle(Number(e.target.value))}>
              {drivable.length === 0 && <option value="">No vehicle has a driver yet</option>}
              {drivable.map(v => <option key={v.id} value={v.id}>{v.registrationNumber} ({v.type})</option>)}
            </select>
            {vehicle && <StatusChip status={vehicle.status} />}
          </div>

          <div className="phone-map">
            <FleetMap vehicles={mapVehicles} deliveries={mapDeliveries}
                      selectedDeliveryId={job ? job.id : undefined}
                      highlightVehicleId={vehicle ? vehicle.id : undefined}
                      follow={followPoint}
                      onMapClick={onMapClick} />
          </div>

          <div className="phone-sheet">
            {driver && <div className="driver-name">👤 {driver.name}</div>}
            {error && <div className="alert">{error}</div>}
            {!vehicle && <p className="muted">Ask an admin to assign a driver to a vehicle.</p>}

            {vehicle && !position && <p className="muted">Tap the map to share your location.</p>}
            {vehicle && position && isStale(vehicle.locationUpdatedAt) && !job && (
              <p className="muted small">Your location is stale, so dispatch can't see you. Tap the map to update it.</p>
            )}

            {vehicle && !job && <WaitingForJob vehicle={vehicle} act={act} />}

            {vehicle && job && (
              <div className="job">
                <div className="card-head">
                  <h3>Job #{job.id}</h3>
                  <StatusChip status={job.status} />
                </div>
                <p className="job-phase">
                  {phaseText}
                  {remainingKm != null && <span className="muted"> · {remainingKm.toFixed(2)} km to go</span>}
                </p>
                <div className="row">
                  {/* One button that switches between Start and Pause */}
                  <button className={tripButtonClass} disabled={!position} onClick={() => setRunning(!running)}>
                    {tripButtonText}
                  </button>
                  <select value={speed} onChange={e => setSpeed(Number(e.target.value))} aria-label="Simulation speed">
                    {SPEEDS.map((s, index) => <option key={s.label} value={index}>{s.label}</option>)}
                  </select>
                </div>
                <JobButtons job={job} act={act} />
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

/** Shown when the vehicle has no job: online/offline status and the button to switch it. */
function WaitingForJob({ vehicle, act }: { vehicle: Vehicle; act: (a: () => Promise<unknown>) => Promise<void> }) {
  let title = 'You are offline';
  if (vehicle.status === 'AVAILABLE') {
    title = 'Online · waiting for a job';
  }
  return (
    <>
      <h3>{title}</h3>
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
  );
}

/** Manual buttons to move the job to its next status without the simulation. */
function JobButtons({ job, act }: { job: Delivery; act: (a: () => Promise<unknown>) => Promise<void> }) {
  return (
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
  );
}
