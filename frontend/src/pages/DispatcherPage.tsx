import { useMemo, useState } from 'react';
import { api } from '../api/client';
import type { Customer, DashboardSummary, Delivery, Driver, LatLng, NearbyVehicle, Vehicle, VehicleType } from '../api/types';
import { ACTIVE_DELIVERY_STATUSES } from '../api/types';
import { FleetMap } from '../components/FleetMap';
import { Header, StatusChip } from '../components/Header';
import { errorMessage } from '../lib/errors';
import { formatPoint, isStale } from '../lib/geo';
import { usePolling } from '../lib/usePolling';

type Tab = 'deliveries' | 'new' | 'fleet';

/** Runs an API action and shows a success or error message. Every tab gets this from the page. */
type RunAction = (action: () => Promise<unknown>, successText: string) => Promise<void>;

/**
 * The dispatcher's screen: stats and three tabs on the left, the live fleet map on the right.
 */
export function DispatcherPage() {
  // Live data, refreshed every 2 seconds. Promise.all runs the three requests at the same time.
  const live = usePolling(async () => {
    const [summary, vehicles, deliveries] = await Promise.all([api.summary(), api.vehicles(), api.deliveries()]);
    return { summary, vehicles, deliveries };
  }, 2000);

  // Drivers and customers change rarely: refresh every 15 seconds
  const directory = usePolling(async () => {
    const [drivers, customers] = await Promise.all([api.drivers(), api.customers()]);
    return { drivers, customers };
  }, 15000);

  const [tab, setTab] = useState<Tab>('deliveries');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [highlightVehicleId, setHighlightVehicleId] = useState<number | null>(null);
  const [notice, setNotice] = useState<{ kind: 'ok' | 'error'; text: string } | null>(null);

  // Draft for "New delivery": first click sets pickup, second sets drop
  const [draftPickup, setDraftPickup] = useState<LatLng | null>(null);
  const [draftDrop, setDraftDrop] = useState<LatLng | null>(null);

  // Before the first load finishes there is no data yet, so start with empty lists
  let vehicles: Vehicle[] = [];
  let deliveries: Delivery[] = [];
  let summary: DashboardSummary | undefined = undefined;
  if (live.data) {
    vehicles = live.data.vehicles;
    deliveries = live.data.deliveries;
    summary = live.data.summary;
  }
  let drivers: Driver[] = [];
  let customers: Customer[] = [];
  if (directory.data) {
    drivers = directory.data.drivers;
    customers = directory.data.customers;
  }

  // The delivery whose detail card is open (null if none)
  let selected: Delivery | null = null;
  for (const delivery of deliveries) {
    if (delivery.id === selectedId) {
      selected = delivery;
      break; // found it, stop looking
    }
  }

  // Show open (CREATED) and active deliveries, plus the selected one even if it has finished.
  // useMemo: only recalculate when the deliveries or the selection change.
  const shownDeliveries = useMemo(() => {
    const result: Delivery[] = [];
    for (const delivery of deliveries) {
      const isOpen = delivery.status === 'CREATED';
      const isActive = ACTIVE_DELIVERY_STATUSES.includes(delivery.status);
      const isSelected = delivery.id === selectedId;
      if (isOpen || isActive || isSelected) {
        result.push(delivery);
      }
    }
    return result;
  }, [deliveries, selectedId]);

  // Runs one action (e.g. "cancel delivery"), shows the result, then refreshes the live data
  async function run(action: () => Promise<unknown>, successText: string) {
    try {
      await action();
      setNotice({ kind: 'ok', text: successText });
      await live.reload();
    } catch (e) {
      setNotice({ kind: 'error', text: errorMessage(e) });
    }
  }

  // On the "New delivery" tab: first click = pickup, second click = drop, third click starts again
  function onMapClick(point: LatLng) {
    if (tab !== 'new') {
      return;
    }
    if (!draftPickup || draftDrop) {
      setDraftPickup(point);
      setDraftDrop(null);
    } else {
      setDraftDrop(point);
    }
  }

  function selectDelivery(delivery: Delivery) {
    setSelectedId(delivery.id);
    setHighlightVehicleId(delivery.vehicleId);
  }

  // Clicking a delivery on the map also switches to the Deliveries tab
  function clickDeliveryOnMap(delivery: Delivery) {
    selectDelivery(delivery);
    setTab('deliveries');
  }

  function resetDraft() {
    setDraftPickup(null);
    setDraftDrop(null);
  }

  // After "Create delivery": clear the draft and open the new delivery's detail card
  function onDeliveryCreated(delivery: Delivery) {
    resetDraft();
    setSelectedId(delivery.id);
    setTab('deliveries');
    directory.reload(); // a new customer may have been created
  }

  function highlightVehicle(vehicle: Vehicle) {
    setHighlightVehicleId(vehicle.id);
  }

  // Name of the driver with this id, or undefined if not loaded yet
  function driverName(driverId: number): string | undefined {
    for (const driver of drivers) {
      if (driver.id === driverId) {
        return driver.name;
      }
    }
    return undefined;
  }

  // ---- what the map shows ----
  const placingRoute = tab === 'new';

  // INACTIVE (deleted) vehicles are not drawn
  const mapVehicles: Vehicle[] = [];
  for (const vehicle of vehicles) {
    if (vehicle.status !== 'INACTIVE') {
      mapVehicles.push(vehicle);
    }
  }

  // While placing a new route, existing pins must not swallow the clicks
  let mapDeliveries = shownDeliveries;
  let mapPickup: LatLng | null = null;
  let mapDrop: LatLng | null = null;
  if (placingRoute) {
    mapDeliveries = [];
    mapPickup = draftPickup;
    mapDrop = draftDrop;
  }

  // The hint at the top of the map while placing a route
  let hintText = 'Route set. Fill in the customer and create the delivery.';
  if (!draftPickup) {
    hintText = 'Click the map to set the pickup point';
  } else if (!draftDrop) {
    hintText = 'Now click the drop point';
  }

  let noticeClass = 'alert';
  if (notice && notice.kind === 'ok') {
    noticeClass = 'toast';
  }

  return (
    <div className="app-shell">
      <Header />
      <div className="dispatch">
        <aside className="panel">
          <div className="stats">
            <Stat label="Available" value={summary?.vehiclesByStatus.AVAILABLE} tone="available" />
            <Stat label="Busy" value={summary?.vehiclesByStatus.BUSY} tone="busy" />
            <Stat label="Offline" value={summary?.vehiclesByStatus.OFFLINE} tone="offline" />
            <Stat label="Active jobs" value={summary?.activeDeliveries} tone="active" />
          </div>

          {live.error && <div className="alert">{live.error}</div>}
          {/* Click the message to close it */}
          {notice && <div className={noticeClass} onClick={() => setNotice(null)}>{notice.text}</div>}

          <div className="tabs" role="tablist">
            <TabButton label="Deliveries" active={tab === 'deliveries'} onClick={() => setTab('deliveries')} />
            <TabButton label="New delivery" active={tab === 'new'} onClick={() => setTab('new')} />
            <TabButton label="Fleet" active={tab === 'fleet'} onClick={() => setTab('fleet')} />
          </div>

          {/* Only the chosen tab is drawn */}
          <div className="panel-body">
            {tab === 'deliveries' && (
              <DeliveriesTab deliveries={shownDeliveries} vehicles={vehicles} selected={selected}
                             onSelect={selectDelivery} run={run} />
            )}
            {tab === 'new' && (
              <NewDeliveryTab customers={customers} pickup={draftPickup} drop={draftDrop}
                              onReset={resetDraft} onCreated={onDeliveryCreated} run={run} />
            )}
            {tab === 'fleet' && (
              <FleetTab vehicles={vehicles} driverName={driverName}
                        highlightId={highlightVehicleId} onSelect={highlightVehicle} run={run} />
            )}
          </div>
        </aside>

        <main className="map-area">
          <FleetMap vehicles={mapVehicles}
                    deliveries={mapDeliveries}
                    vehiclesClickable={!placingRoute}
                    selectedDeliveryId={selectedId}
                    highlightVehicleId={highlightVehicleId}
                    draftPickup={mapPickup}
                    draftDrop={mapDrop}
                    crosshair={placingRoute}
                    onMapClick={onMapClick}
                    onVehicleClick={highlightVehicle}
                    onDeliveryClick={clickDeliveryOnMap} />
          {placingRoute && <div className="map-hint">{hintText}</div>}
          <Legend />
        </main>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------

function TabButton({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return <button className={active ? 'active' : ''} onClick={onClick}>{label}</button>;
}

/** Registration number of the vehicle with this id, or a dash. */
function registrationOf(vehicles: Vehicle[], vehicleId: number | null): string {
  for (const vehicle of vehicles) {
    if (vehicle.id === vehicleId) {
      return vehicle.registrationNumber;
    }
  }
  return '—';
}

function DeliveriesTab({ deliveries, vehicles, selected, onSelect, run }: {
  deliveries: Delivery[]; vehicles: Vehicle[]; selected: Delivery | null;
  onSelect: (d: Delivery) => void; run: RunAction;
}) {
  let selectedId: number | null = null;
  if (selected) {
    selectedId = selected.id;
  }

  return (
    <>
      {selected && <DeliveryDetail delivery={selected} vehicleReg={registrationOf(vehicles, selected.vehicleId)} run={run} />}
      <h3 className="section-title">Open and active</h3>
      {deliveries.length === 0 && <p className="muted">No open deliveries. Create one in “New delivery”.</p>}
      <ul className="list">
        {/* .map() turns each delivery into one list row */}
        {deliveries.map(delivery => (
          <li key={delivery.id} className={delivery.id === selectedId ? 'selected' : ''} onClick={() => onSelect(delivery)}>
            <div>
              <strong>Delivery #{delivery.id}</strong>
              <div className="muted small">Vehicle {registrationOf(vehicles, delivery.vehicleId)}</div>
            </div>
            <StatusChip status={delivery.status} />
          </li>
        ))}
      </ul>
    </>
  );
}

/** A short explanation under the detail card for each status that has one. */
const STATUS_HELP_TEXT: Partial<Record<Delivery['status'], string>> = {
  ASSIGNED: 'The driver is heading to the pickup. Open the Driver app to simulate the trip.',
  PICKED_UP: 'Parcel picked up.',
  IN_TRANSIT: 'On the way to the drop point.',
  DELIVERED: 'Delivered. The vehicle is available again.',
};

const NEARBY_RADIUS_KM = 25;

function DeliveryDetail({ delivery, vehicleReg, run }: {
  delivery: Delivery; vehicleReg: string; run: RunAction;
}) {
  // Vehicles near the pickup, loaded when "Choose vehicle…" is clicked (null = not loaded yet)
  const [nearby, setNearby] = useState<NearbyVehicle[] | null>(null);

  async function findNearby() {
    const lat = delivery.pickupLatitude;
    const lng = delivery.pickupLongitude;
    if (lat == null || lng == null) {
      return;
    }
    await run(async () => {
      const found = await api.nearby(lat, lng, NEARBY_RADIUS_KM);
      setNearby(found);
    }, 'Nearby available vehicles loaded');
  }

  function autoAssign() {
    run(() => api.autoAssign(delivery.id), 'Assigned the nearest available vehicle');
  }

  function cancel() {
    run(() => api.setDeliveryStatus(delivery.id, 'CANCELLED'), 'Delivery #' + delivery.id + ' cancelled');
  }

  function assignVehicle(vehicle: NearbyVehicle) {
    run(() => api.assign(delivery.id, vehicle.vehicleId), 'Assigned ' + vehicle.registrationNumber);
  }

  const isCreated = delivery.status === 'CREATED';
  // Only deliveries that have not been picked up yet can be cancelled
  const canCancel = delivery.status === 'CREATED' || delivery.status === 'ASSIGNED';
  const helpText = STATUS_HELP_TEXT[delivery.status];

  return (
    <div className="card">
      <div className="card-head">
        <h3>Delivery #{delivery.id}</h3>
        <StatusChip status={delivery.status} />
      </div>
      <dl className="facts">
        <dt>Vehicle</dt><dd>{vehicleReg}</dd>
        <dt>Pickup</dt><dd>{formatPoint(delivery.pickupLatitude, delivery.pickupLongitude)}</dd>
        <dt>Drop</dt><dd>{formatPoint(delivery.dropLatitude, delivery.dropLongitude)}</dd>
      </dl>
      <div className="actions">
        {isCreated && (
          <>
            <button className="btn btn-primary" onClick={autoAssign}>Auto-assign nearest</button>
            <button className="btn" onClick={findNearby}>Choose vehicle…</button>
          </>
        )}
        {canCancel && <button className="btn btn-danger" onClick={cancel}>Cancel</button>}
      </div>
      {isCreated && nearby && (
        <ul className="list compact">
          {nearby.length === 0 && <li className="muted">No available vehicle with a fresh location within 25 km.</li>}
          {nearby.map(vehicle => (
            <li key={vehicle.vehicleId}>
              <div>
                <strong>{vehicle.registrationNumber}</strong>
                <div className="muted small">{vehicle.distanceKm} km · {vehicle.driverId ? 'has driver' : 'no driver'}</div>
              </div>
              {/* A vehicle without a driver can't take the job */}
              <button className="btn btn-small" disabled={!vehicle.driverId} onClick={() => assignVehicle(vehicle)}>
                Assign
              </button>
            </li>
          ))}
        </ul>
      )}
      {helpText && <p className="muted small">{helpText}</p>}
    </div>
  );
}

function NewDeliveryTab({ customers, pickup, drop, onReset, onCreated, run }: {
  customers: Customer[]; pickup: LatLng | null; drop: LatLng | null; onReset: () => void;
  onCreated: (d: Delivery) => void; run: RunAction;
}) {
  const [customerId, setCustomerId] = useState<string>('new'); // 'new' = create a new customer
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [autoAssign, setAutoAssign] = useState(true);

  // The button is enabled once the route is set and the customer is chosen (or typed in)
  const isNewCustomer = customerId === 'new';
  const newCustomerFilled = name.trim() !== '' && phone.trim() !== '';
  const routeSet = pickup !== null && drop !== null;
  const ready = routeSet && (!isNewCustomer || newCustomerFilled);

  async function create() {
    if (!pickup || !drop) {
      return;
    }

    let successText = 'Delivery created';
    if (autoAssign) {
      successText = 'Delivery created and assigned to the nearest vehicle';
    }

    await run(async () => {
      // 1) Find the customer id: an existing customer, or create a new one with this route
      let finalCustomerId: number;
      if (isNewCustomer) {
        const customer = await api.createCustomer({
          name: name.trim(),
          phone: phone.trim(),
          pickupLatitude: pickup.lat,
          pickupLongitude: pickup.lng,
          dropLatitude: drop.lat,
          dropLongitude: drop.lng,
        });
        finalCustomerId = customer.id;
      } else {
        finalCustomerId = Number(customerId);
      }

      // 2) Create the delivery, 3) optionally assign the nearest vehicle straight away
      const delivery = await api.createDelivery(finalCustomerId, [pickup.lat, pickup.lng], [drop.lat, drop.lng]);
      onCreated(delivery);
      if (autoAssign) {
        await api.autoAssign(delivery.id);
      }
    }, successText);
  }

  // The 3 steps at the top: 'done', 'current' (the next thing to do), or '' (not yet)
  let pickupStep = 'current';
  if (pickup) {
    pickupStep = 'done';
  }
  let dropStep = '';
  if (drop) {
    dropStep = 'done';
  } else if (pickup) {
    dropStep = 'current';
  }
  let customerStep = '';
  if (routeSet) {
    customerStep = 'current';
  }

  return (
    <div className="form">
      <ol className="steps">
        <li className={pickupStep}>Click the map to set the <b>pickup</b> {pickup && '(' + formatPoint(pickup.lat, pickup.lng) + ')'}</li>
        <li className={dropStep}>Click again for the <b>drop</b> {drop && '(' + formatPoint(drop.lat, drop.lng) + ')'}</li>
        <li className={customerStep}>Choose the customer</li>
      </ol>
      {(pickup || drop) && <button className="btn btn-ghost btn-small" onClick={onReset}>Reset route</button>}

      <label>
        Customer
        <select value={customerId} onChange={e => setCustomerId(e.target.value)}>
          <option value="new">+ New customer</option>
          {customers.map(customer => (
            <option key={customer.id} value={customer.id}>{customer.name} · {customer.phone}</option>
          ))}
        </select>
      </label>
      {isNewCustomer && (
        <div className="row">
          <label>Name<input value={name} onChange={e => setName(e.target.value)} placeholder="Asha" /></label>
          <label>Phone<input value={phone} onChange={e => setPhone(e.target.value)} placeholder="9000000000" /></label>
        </div>
      )}
      <label className="checkbox">
        <input type="checkbox" checked={autoAssign} onChange={e => setAutoAssign(e.target.checked)} />
        Auto-assign the nearest vehicle right away
      </label>
      <button className="btn btn-primary btn-block" disabled={!ready} onClick={create}>Create delivery</button>
    </div>
  );
}

/** Second line of a vehicle row, e.g. "Driver: Ravi · location stale". */
function vehicleDetails(vehicle: Vehicle, driverName: (id: number) => string | undefined): string {
  let text = 'No driver';
  if (vehicle.driverId) {
    const name = driverName(vehicle.driverId);
    if (name) {
      text = 'Driver: ' + name;
    } else {
      text = 'Driver: #' + vehicle.driverId; // name not loaded yet
    }
  }

  if (vehicle.currentLatitude == null) {
    text = text + ' · no location';
  } else if (isStale(vehicle.locationUpdatedAt)) {
    text = text + ' · location stale';
  }
  return text;
}

function FleetTab({ vehicles, driverName, highlightId, onSelect, run }: {
  vehicles: Vehicle[]; driverName: (id: number) => string | undefined; highlightId: number | null;
  onSelect: (v: Vehicle) => void; run: RunAction;
}) {
  // The "add vehicle" form
  const [registration, setRegistration] = useState('');
  const [type, setType] = useState<VehicleType>('VAN');

  function addVehicle() {
    run(async () => {
      await api.createVehicle(registration.trim(), type);
      setRegistration(''); // clear the box after success
    }, 'Vehicle added');
  }

  return (
    <>
      <div className="row add-vehicle">
        <input value={registration} onChange={e => setRegistration(e.target.value)} placeholder="New registration no." />
        <select value={type} onChange={e => setType(e.target.value as VehicleType)}>
          <option>BIKE</option><option>VAN</option><option>TRUCK</option>
        </select>
        <button className="btn btn-small" disabled={!registration.trim()} onClick={addVehicle}>Add</button>
      </div>
      <ul className="list">
        {vehicles.map(vehicle => (
          <li key={vehicle.id} className={vehicle.id === highlightId ? 'selected' : ''} onClick={() => onSelect(vehicle)}>
            <div>
              <strong>{vehicle.registrationNumber}</strong> <span className="muted small">{vehicle.type}</span>
              <div className="muted small">{vehicleDetails(vehicle, driverName)}</div>
            </div>
            <StatusChip status={vehicle.status} />
          </li>
        ))}
      </ul>
    </>
  );
}

/** One number box at the top, e.g. "3 Available". Shows a dash until the data has loaded. */
function Stat({ label, value, tone }: { label: string; value: number | undefined; tone: string }) {
  let shown: string | number = '–';
  if (value !== undefined && value !== null) {
    shown = value;
  }
  return (
    <div className={'stat stat-' + tone}>
      <div className="stat-value">{shown}</div>
      <div className="stat-label">{label}</div>
    </div>
  );
}

/** Colour key in the corner of the map. */
function Legend() {
  return (
    <div className="legend">
      <span><i className="dot status-available" /> Available</span>
      <span><i className="dot status-busy" /> Busy</span>
      <span><i className="dot status-offline" /> Offline</span>
      <span><i className="dot stale" /> Stale location</span>
    </div>
  );
}
