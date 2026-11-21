import { useMemo, useState } from 'react';
import { api } from '../api/client';
import type { Customer, Delivery, LatLng, NearbyVehicle, Vehicle, VehicleType } from '../api/types';
import { ACTIVE_DELIVERY_STATUSES } from '../api/types';
import { FleetMap } from '../components/FleetMap';
import { Header, StatusChip } from '../components/Header';
import { isStale } from '../lib/geo';
import { usePolling } from '../lib/usePolling';

type Tab = 'deliveries' | 'new' | 'fleet';

export function DispatcherPage() {
  const live = usePolling(async () => {
    const [summary, vehicles, deliveries] = await Promise.all([api.summary(), api.vehicles(), api.deliveries()]);
    return { summary, vehicles, deliveries };
  }, 2000);
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

  const vehicles = live.data?.vehicles ?? [];
  const deliveries = live.data?.deliveries ?? [];
  const summary = live.data?.summary;
  const selected = deliveries.find(d => d.id === selectedId) ?? null;

  const shownDeliveries = useMemo(
    () => deliveries.filter(d => d.status === 'CREATED' || ACTIVE_DELIVERY_STATUSES.includes(d.status) || d.id === selectedId),
    [deliveries, selectedId],
  );

  async function run(action: () => Promise<unknown>, success: string) {
    try {
      await action();
      setNotice({ kind: 'ok', text: success });
      await live.reload();
    } catch (e) {
      setNotice({ kind: 'error', text: e instanceof Error ? e.message : String(e) });
    }
  }

  function onMapClick(p: LatLng) {
    if (tab !== 'new') return;
    if (!draftPickup || draftDrop) {
      setDraftPickup(p);
      setDraftDrop(null);
    } else {
      setDraftDrop(p);
    }
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
          {notice && (
            <div className={notice.kind === 'ok' ? 'toast' : 'alert'} onClick={() => setNotice(null)}>{notice.text}</div>
          )}

          <div className="tabs" role="tablist">
            <button className={tab === 'deliveries' ? 'active' : ''} onClick={() => setTab('deliveries')}>Deliveries</button>
            <button className={tab === 'new' ? 'active' : ''} onClick={() => setTab('new')}>New delivery</button>
            <button className={tab === 'fleet' ? 'active' : ''} onClick={() => setTab('fleet')}>Fleet</button>
          </div>

          <div className="panel-body">
            {tab === 'deliveries' && (
              <DeliveriesTab deliveries={shownDeliveries} vehicles={vehicles} selected={selected}
                             onSelect={d => { setSelectedId(d.id); setHighlightVehicleId(d.vehicleId); }}
                             run={run} />
            )}
            {tab === 'new' && (
              <NewDeliveryTab customers={directory.data?.customers ?? []} pickup={draftPickup} drop={draftDrop}
                              onReset={() => { setDraftPickup(null); setDraftDrop(null); }}
                              onCreated={d => {
                                setDraftPickup(null); setDraftDrop(null);
                                setSelectedId(d.id); setTab('deliveries');
                                directory.reload();
                              }}
                              run={run} />
            )}
            {tab === 'fleet' && (
              <FleetTab vehicles={vehicles} driverName={id => directory.data?.drivers.find(d => d.id === id)?.name}
                        highlightId={highlightVehicleId} onSelect={v => setHighlightVehicleId(v.id)} run={run} />
            )}
          </div>
        </aside>

        <main className="map-area">
          <FleetMap vehicles={vehicles.filter(v => v.status !== 'INACTIVE')}
                    // While placing a new route, existing pins must not swallow the clicks
                    deliveries={tab === 'new' ? [] : shownDeliveries}
                    vehiclesClickable={tab !== 'new'}
                    selectedDeliveryId={selectedId}
                    highlightVehicleId={highlightVehicleId}
                    draftPickup={tab === 'new' ? draftPickup : null}
                    draftDrop={tab === 'new' ? draftDrop : null}
                    crosshair={tab === 'new'}
                    onMapClick={onMapClick}
                    onVehicleClick={v => setHighlightVehicleId(v.id)}
                    onDeliveryClick={d => { setSelectedId(d.id); setTab('deliveries'); setHighlightVehicleId(d.vehicleId); }} />
          {tab === 'new' && (
            <div className="map-hint">
              {!draftPickup ? 'Click the map to set the pickup point'
                : !draftDrop ? 'Now click the drop point' : 'Route set. Fill in the customer and create the delivery.'}
            </div>
          )}
          <Legend />
        </main>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------

function DeliveriesTab({ deliveries, vehicles, selected, onSelect, run }: {
  deliveries: Delivery[]; vehicles: Vehicle[]; selected: Delivery | null;
  onSelect: (d: Delivery) => void; run: (a: () => Promise<unknown>, ok: string) => Promise<void>;
}) {
  const reg = (id: number | null) => vehicles.find(v => v.id === id)?.registrationNumber ?? '—';
  return (
    <>
      {selected && <DeliveryDetail delivery={selected} vehicleReg={reg(selected.vehicleId)} run={run} />}
      <h3 className="section-title">Open and active</h3>
      {deliveries.length === 0 && <p className="muted">No open deliveries. Create one in “New delivery”.</p>}
      <ul className="list">
        {deliveries.map(d => (
          <li key={d.id} className={selected?.id === d.id ? 'selected' : ''} onClick={() => onSelect(d)}>
            <div>
              <strong>Delivery #{d.id}</strong>
              <div className="muted small">Vehicle {reg(d.vehicleId)}</div>
            </div>
            <StatusChip status={d.status} />
          </li>
        ))}
      </ul>
    </>
  );
}

function DeliveryDetail({ delivery: d, vehicleReg, run }: {
  delivery: Delivery; vehicleReg: string; run: (a: () => Promise<unknown>, ok: string) => Promise<void>;
}) {
  const [nearby, setNearby] = useState<NearbyVehicle[] | null>(null);

  async function findNearby() {
    if (d.pickupLatitude == null || d.pickupLongitude == null) return;
    await run(async () => setNearby(await api.nearby(d.pickupLatitude!, d.pickupLongitude!, 25)),
      'Nearby available vehicles loaded');
  }

  return (
    <div className="card">
      <div className="card-head">
        <h3>Delivery #{d.id}</h3>
        <StatusChip status={d.status} />
      </div>
      <dl className="facts">
        <dt>Vehicle</dt><dd>{vehicleReg}</dd>
        <dt>Pickup</dt><dd>{fmt(d.pickupLatitude, d.pickupLongitude)}</dd>
        <dt>Drop</dt><dd>{fmt(d.dropLatitude, d.dropLongitude)}</dd>
      </dl>
      <div className="actions">
        {d.status === 'CREATED' && (
          <>
            <button className="btn btn-primary"
                    onClick={() => run(async () => {
                      const r = await api.autoAssign(d.id);
                      return r;
                    }, 'Assigned the nearest available vehicle')}>
              Auto-assign nearest
            </button>
            <button className="btn" onClick={findNearby}>Choose vehicle…</button>
          </>
        )}
        {(d.status === 'CREATED' || d.status === 'ASSIGNED') && (
          <button className="btn btn-danger" onClick={() => run(() => api.setDeliveryStatus(d.id, 'CANCELLED'), `Delivery #${d.id} cancelled`)}>
            Cancel
          </button>
        )}
      </div>
      {d.status === 'CREATED' && nearby && (
        <ul className="list compact">
          {nearby.length === 0 && <li className="muted">No available vehicle with a fresh location within 25 km.</li>}
          {nearby.map(n => (
            <li key={n.vehicleId}>
              <div>
                <strong>{n.registrationNumber}</strong>
                <div className="muted small">{n.distanceKm} km · {n.driverId ? 'has driver' : 'no driver'}</div>
              </div>
              <button className="btn btn-small" disabled={!n.driverId}
                      onClick={() => run(() => api.assign(d.id, n.vehicleId), `Assigned ${n.registrationNumber}`)}>
                Assign
              </button>
            </li>
          ))}
        </ul>
      )}
      {ACTIVE_STATUSES_TEXT[d.status] && <p className="muted small">{ACTIVE_STATUSES_TEXT[d.status]}</p>}
    </div>
  );
}

const ACTIVE_STATUSES_TEXT: Partial<Record<Delivery['status'], string>> = {
  ASSIGNED: 'The driver is heading to the pickup. Open the Driver app to simulate the trip.',
  PICKED_UP: 'Parcel picked up.',
  IN_TRANSIT: 'On the way to the drop point.',
  DELIVERED: 'Delivered. The vehicle is available again.',
};

function NewDeliveryTab({ customers, pickup, drop, onReset, onCreated, run }: {
  customers: Customer[]; pickup: LatLng | null; drop: LatLng | null; onReset: () => void;
  onCreated: (d: Delivery) => void; run: (a: () => Promise<unknown>, ok: string) => Promise<void>;
}) {
  const [customerId, setCustomerId] = useState<string>('new');
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [autoAssign, setAutoAssign] = useState(true);
  const ready = pickup && drop && (customerId !== 'new' || (name.trim() && phone.trim()));

  async function create() {
    if (!pickup || !drop) return;
    await run(async () => {
      const cid = customerId !== 'new' ? Number(customerId) : (await api.createCustomer({
        name: name.trim(), phone: phone.trim(),
        pickupLatitude: pickup.lat, pickupLongitude: pickup.lng, dropLatitude: drop.lat, dropLongitude: drop.lng,
      })).id;
      const delivery = await api.createDelivery(cid, [pickup.lat, pickup.lng], [drop.lat, drop.lng]);
      onCreated(delivery);
      if (autoAssign) await api.autoAssign(delivery.id);
    }, autoAssign ? 'Delivery created and assigned to the nearest vehicle' : 'Delivery created');
  }

  return (
    <div className="form">
      <ol className="steps">
        <li className={pickup ? 'done' : 'current'}>Click the map to set the <b>pickup</b> {pickup && `(${fmt(pickup.lat, pickup.lng)})`}</li>
        <li className={drop ? 'done' : pickup ? 'current' : ''}>Click again for the <b>drop</b> {drop && `(${fmt(drop.lat, drop.lng)})`}</li>
        <li className={pickup && drop ? 'current' : ''}>Choose the customer</li>
      </ol>
      {(pickup || drop) && <button className="btn btn-ghost btn-small" onClick={onReset}>Reset route</button>}

      <label>
        Customer
        <select value={customerId} onChange={e => setCustomerId(e.target.value)}>
          <option value="new">+ New customer</option>
          {customers.map(c => <option key={c.id} value={c.id}>{c.name} · {c.phone}</option>)}
        </select>
      </label>
      {customerId === 'new' && (
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

function FleetTab({ vehicles, driverName, highlightId, onSelect, run }: {
  vehicles: Vehicle[]; driverName: (id: number) => string | undefined; highlightId: number | null;
  onSelect: (v: Vehicle) => void; run: (a: () => Promise<unknown>, ok: string) => Promise<void>;
}) {
  const [reg, setReg] = useState('');
  const [type, setType] = useState<VehicleType>('VAN');
  return (
    <>
      <div className="row add-vehicle">
        <input value={reg} onChange={e => setReg(e.target.value)} placeholder="New registration no." />
        <select value={type} onChange={e => setType(e.target.value as VehicleType)}>
          <option>BIKE</option><option>VAN</option><option>TRUCK</option>
        </select>
        <button className="btn btn-small" disabled={!reg.trim()}
                onClick={() => run(async () => { await api.createVehicle(reg.trim(), type); setReg(''); }, 'Vehicle added')}>
          Add
        </button>
      </div>
      <ul className="list">
        {vehicles.map(v => (
          <li key={v.id} className={v.id === highlightId ? 'selected' : ''} onClick={() => onSelect(v)}>
            <div>
              <strong>{v.registrationNumber}</strong> <span className="muted small">{v.type}</span>
              <div className="muted small">
                {v.driverId ? `Driver: ${driverName(v.driverId) ?? '#' + v.driverId}` : 'No driver'}
                {v.currentLatitude == null ? ' · no location' : isStale(v.locationUpdatedAt) ? ' · location stale' : ''}
              </div>
            </div>
            <StatusChip status={v.status} />
          </li>
        ))}
      </ul>
    </>
  );
}

function Stat({ label, value, tone }: { label: string; value: number | undefined; tone: string }) {
  return (
    <div className={`stat stat-${tone}`}>
      <div className="stat-value">{value ?? '–'}</div>
      <div className="stat-label">{label}</div>
    </div>
  );
}

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

const fmt = (lat: number | null, lng: number | null) =>
  lat == null || lng == null ? '—' : `${lat.toFixed(4)}, ${lng.toFixed(4)}`;
