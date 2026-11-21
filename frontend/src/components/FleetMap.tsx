import L from 'leaflet';
import { useEffect, useRef } from 'react';
import { MapContainer, Marker, Polyline, TileLayer, Tooltip, useMap, useMapEvents } from 'react-leaflet';
import type { Delivery, LatLng, Vehicle } from '../api/types';
import { isStale } from '../lib/geo';

const VEHICLE_EMOJI = { BIKE: '🏍️', VAN: '🚐', TRUCK: '🚚' } as const;
const DEFAULT_CENTER: [number, number] = [18.5204, 73.8567]; // Pune

// divIcons avoid Leaflet's default image markers, whose asset paths break under bundlers.
function vehicleIcon(v: Vehicle, highlighted: boolean) {
  const stale = isStale(v.locationUpdatedAt);
  const cls = `vehicle-pin status-${v.status.toLowerCase()}${stale ? ' stale' : ''}${highlighted ? ' highlighted' : ''}`;
  return L.divIcon({
    className: '',
    html: `<div class="${cls}">${VEHICLE_EMOJI[v.type]}</div>`,
    iconSize: [36, 36],
    iconAnchor: [18, 18],
  });
}

const pinIcon = (kind: 'pickup' | 'drop', label: string) =>
  L.divIcon({
    className: '',
    html: `<div class="place-pin ${kind}"><span>${label}</span></div>`,
    iconSize: [26, 26],
    iconAnchor: [13, 26],
  });

interface Props {
  vehicles: Vehicle[];
  deliveries?: Delivery[];
  selectedDeliveryId?: number | null;
  highlightVehicleId?: number | null;
  draftPickup?: LatLng | null;
  draftDrop?: LatLng | null;
  onMapClick?: (point: LatLng) => void;
  onVehicleClick?: (vehicle: Vehicle) => void;
  onDeliveryClick?: (delivery: Delivery) => void;
  follow?: LatLng | null;
  crosshair?: boolean;
  /** false lets clicks on vehicle pins fall through to the map (used while placing points). */
  vehiclesClickable?: boolean;
}

export function FleetMap(props: Props) {
  const located = props.vehicles.filter(v => v.currentLatitude != null && v.currentLongitude != null);
  return (
    <MapContainer center={DEFAULT_CENTER} zoom={12} className={`map${props.crosshair ? ' crosshair' : ''}`}
                  zoomControl={true}>
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <FitToVehiclesOnce vehicles={located} />
      {props.follow && <Follow point={props.follow} />}
      <ClickHandler onClick={props.onMapClick} />

      {(props.deliveries ?? []).map(d => <DeliveryOverlay key={d.id} delivery={d}
        vehicle={located.find(v => v.id === d.vehicleId)}
        selected={d.id === props.selectedDeliveryId}
        onClick={props.onDeliveryClick} />)}

      {props.draftPickup && <Marker position={props.draftPickup} icon={pinIcon('pickup', 'P')} />}
      {props.draftDrop && <Marker position={props.draftDrop} icon={pinIcon('drop', 'D')} />}
      {props.draftPickup && props.draftDrop &&
        <Polyline positions={[props.draftPickup, props.draftDrop]} pathOptions={{ className: 'route draft' }} />}

      {located.map(v => (
        <Marker key={v.id} position={[v.currentLatitude!, v.currentLongitude!]}
                icon={vehicleIcon(v, v.id === props.highlightVehicleId)}
                zIndexOffset={v.id === props.highlightVehicleId ? 1000 : 0}
                interactive={props.vehiclesClickable !== false}
                eventHandlers={{ click: () => props.onVehicleClick?.(v) }}>
          <Tooltip direction="top" offset={[0, -16]}>
            <strong>{v.registrationNumber}</strong> · {v.status}
            {isStale(v.locationUpdatedAt) && ' · location stale'}
          </Tooltip>
        </Marker>
      ))}
    </MapContainer>
  );
}

function DeliveryOverlay({ delivery: d, vehicle, selected, onClick }: {
  delivery: Delivery; vehicle?: Vehicle; selected: boolean; onClick?: (d: Delivery) => void;
}) {
  if (d.pickupLatitude == null || d.pickupLongitude == null || d.dropLatitude == null || d.dropLongitude == null) {
    return null;
  }
  const pickup: [number, number] = [d.pickupLatitude, d.pickupLongitude];
  const drop: [number, number] = [d.dropLatitude, d.dropLongitude];
  const handlers = { click: () => onClick?.(d) };
  // Vehicle -> pickup while going to collect, vehicle -> drop once loaded
  const leg: [number, number] | null = vehicle
    ? d.status === 'ASSIGNED' ? pickup : d.status === 'IN_TRANSIT' || d.status === 'PICKED_UP' ? drop : null
    : null;
  return (
    <>
      <Polyline positions={[pickup, drop]} pathOptions={{ className: `route${selected ? ' selected' : ''}` }}
                eventHandlers={handlers} />
      {leg && vehicle && <Polyline positions={[[vehicle.currentLatitude!, vehicle.currentLongitude!], leg]}
                                   pathOptions={{ className: 'route live' }} />}
      <Marker position={pickup} icon={pinIcon('pickup', 'P')} eventHandlers={handlers}>
        <Tooltip direction="top" offset={[0, -24]}>Delivery #{d.id} pickup · {d.status}</Tooltip>
      </Marker>
      <Marker position={drop} icon={pinIcon('drop', 'D')} eventHandlers={handlers}>
        <Tooltip direction="top" offset={[0, -24]}>Delivery #{d.id} drop</Tooltip>
      </Marker>
    </>
  );
}

function ClickHandler({ onClick }: { onClick?: (p: LatLng) => void }) {
  useMapEvents({ click: e => onClick?.({ lat: round(e.latlng.lat), lng: round(e.latlng.lng) }) });
  return null;
}

function FitToVehiclesOnce({ vehicles }: { vehicles: Vehicle[] }) {
  const map = useMap();
  const done = useRef(false);
  useEffect(() => {
    if (done.current || vehicles.length === 0) return;
    done.current = true;
    const bounds = L.latLngBounds(vehicles.map(v => [v.currentLatitude!, v.currentLongitude!]));
    map.fitBounds(bounds.pad(0.3), { maxZoom: 14 });
  }, [vehicles, map]);
  return null;
}

function Follow({ point }: { point: LatLng }) {
  const map = useMap();
  useEffect(() => {
    map.panTo([point.lat, point.lng], { animate: true });
  }, [point.lat, point.lng, map]);
  return null;
}

const round = (n: number) => Math.round(n * 1e5) / 1e5;
