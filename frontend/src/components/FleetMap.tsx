import L from 'leaflet';
import { useEffect, useRef } from 'react';
import { MapContainer, Marker, Polyline, TileLayer, Tooltip, useMap, useMapEvents } from 'react-leaflet';
import type { Delivery, LatLng, Vehicle } from '../api/types';
import { isStale } from '../lib/geo';

const VEHICLE_EMOJI = { BIKE: '🏍️', VAN: '🚐', TRUCK: '🚚' } as const;
const DEFAULT_CENTER: [number, number] = [18.5204, 73.8567]; // Pune

/**
 * The map pin for a vehicle: an emoji in a coloured circle (colour = status, set in styles.css).
 * divIcons avoid Leaflet's default image markers, whose asset paths break under bundlers.
 */
function vehicleIcon(vehicle: Vehicle, highlighted: boolean) {
  // CSS classes, e.g. "vehicle-pin status-busy stale highlighted"
  let cssClass = 'vehicle-pin status-' + vehicle.status.toLowerCase();
  if (isStale(vehicle.locationUpdatedAt)) {
    cssClass = cssClass + ' stale';
  }
  if (highlighted) {
    cssClass = cssClass + ' highlighted';
  }

  return L.divIcon({
    className: '',
    html: '<div class="' + cssClass + '">' + VEHICLE_EMOJI[vehicle.type] + '</div>',
    iconSize: [36, 36],
    iconAnchor: [18, 18], // the centre of the circle sits on the vehicle's position
  });
}

/** A "P" (pickup) or "D" (drop) pin. */
function pinIcon(kind: 'pickup' | 'drop', label: string) {
  return L.divIcon({
    className: '',
    html: '<div class="place-pin ' + kind + '"><span>' + label + '</span></div>',
    iconSize: [26, 26],
    iconAnchor: [13, 26], // the pin's tip (bottom middle) sits on the point
  });
}

/** Rounds a coordinate to 5 decimals (about 1 metre). */
function round5(value: number): number {
  return Math.round(value * 100000) / 100000;
}

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
  // Only vehicles that have reported a position can be drawn
  const located: Vehicle[] = [];
  for (const vehicle of props.vehicles) {
    if (vehicle.currentLatitude != null && vehicle.currentLongitude != null) {
      located.push(vehicle);
    }
  }

  let deliveries: Delivery[] = [];
  if (props.deliveries) {
    deliveries = props.deliveries;
  }

  let mapClass = 'map';
  if (props.crosshair) {
    mapClass = 'map crosshair';
  }

  // Vehicle pins are clickable unless the caller turned that off
  const vehiclesClickable = props.vehiclesClickable !== false;

  // Tell the parent page which vehicle was clicked (if it wants to know)
  function clickVehicle(vehicle: Vehicle) {
    if (props.onVehicleClick) {
      props.onVehicleClick(vehicle);
    }
  }

  return (
    <MapContainer center={DEFAULT_CENTER} zoom={12} className={mapClass} zoomControl={true}>
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <FitToVehiclesOnce vehicles={located} />
      {props.follow && <Follow point={props.follow} />}
      <ClickHandler onClick={props.onMapClick} />

      {/* .map() turns each delivery into its route lines and pins */}
      {deliveries.map(delivery => (
        <DeliveryOverlay key={delivery.id}
                         delivery={delivery}
                         vehicle={findVehicle(located, delivery.vehicleId)}
                         selected={delivery.id === props.selectedDeliveryId}
                         onClick={props.onDeliveryClick} />
      ))}

      {/* The route being drawn on the "New delivery" tab */}
      {props.draftPickup && <Marker position={props.draftPickup} icon={pinIcon('pickup', 'P')} />}
      {props.draftDrop && <Marker position={props.draftDrop} icon={pinIcon('drop', 'D')} />}
      {props.draftPickup && props.draftDrop && (
        <Polyline positions={[props.draftPickup, props.draftDrop]} pathOptions={{ className: 'route draft' }} />
      )}

      {/* One pin per vehicle */}
      {located.map(vehicle => {
        const highlighted = vehicle.id === props.highlightVehicleId;
        // The highlighted pin is drawn on top of the others
        let zIndex = 0;
        if (highlighted) {
          zIndex = 1000;
        }
        return (
          <Marker key={vehicle.id}
                  position={[vehicle.currentLatitude!, vehicle.currentLongitude!]}
                  icon={vehicleIcon(vehicle, highlighted)}
                  zIndexOffset={zIndex}
                  interactive={vehiclesClickable}
                  eventHandlers={{ click: () => clickVehicle(vehicle) }}>
            <Tooltip direction="top" offset={[0, -16]}>
              <strong>{vehicle.registrationNumber}</strong> · {vehicle.status}
              {isStale(vehicle.locationUpdatedAt) && ' · location stale'}
            </Tooltip>
          </Marker>
        );
      })}
    </MapContainer>
  );
}

/** The vehicle with this id, or undefined if there is none. */
function findVehicle(vehicles: Vehicle[], id: number | null): Vehicle | undefined {
  for (const vehicle of vehicles) {
    if (vehicle.id === id) {
      return vehicle;
    }
  }
  return undefined;
}

/** Draws one delivery: pickup pin, drop pin, the route between them, and the vehicle's current leg. */
function DeliveryOverlay({ delivery, vehicle, selected, onClick }: {
  delivery: Delivery; vehicle?: Vehicle; selected: boolean; onClick?: (d: Delivery) => void;
}) {
  // A delivery without a full route can't be drawn
  if (delivery.pickupLatitude == null || delivery.pickupLongitude == null
      || delivery.dropLatitude == null || delivery.dropLongitude == null) {
    return null;
  }
  const pickup: [number, number] = [delivery.pickupLatitude, delivery.pickupLongitude];
  const drop: [number, number] = [delivery.dropLatitude, delivery.dropLongitude];

  // Clicking the line or a pin selects this delivery
  function clickDelivery() {
    if (onClick) {
      onClick(delivery);
    }
  }
  const handlers = { click: clickDelivery };

  // The live leg: vehicle -> pickup while going to collect, vehicle -> drop once loaded
  let legEnd: [number, number] | null = null;
  if (vehicle) {
    if (delivery.status === 'ASSIGNED') {
      legEnd = pickup;
    } else if (delivery.status === 'PICKED_UP' || delivery.status === 'IN_TRANSIT') {
      legEnd = drop;
    }
  }

  let routeClass = 'route';
  if (selected) {
    routeClass = 'route selected';
  }

  return (
    <>
      <Polyline positions={[pickup, drop]} pathOptions={{ className: routeClass }} eventHandlers={handlers} />
      {legEnd && vehicle && (
        <Polyline positions={[[vehicle.currentLatitude!, vehicle.currentLongitude!], legEnd]}
                  pathOptions={{ className: 'route live' }} />
      )}
      <Marker position={pickup} icon={pinIcon('pickup', 'P')} eventHandlers={handlers}>
        <Tooltip direction="top" offset={[0, -24]}>Delivery #{delivery.id} pickup · {delivery.status}</Tooltip>
      </Marker>
      <Marker position={drop} icon={pinIcon('drop', 'D')} eventHandlers={handlers}>
        <Tooltip direction="top" offset={[0, -24]}>Delivery #{delivery.id} drop</Tooltip>
      </Marker>
    </>
  );
}

/** Sends map clicks to the parent as a rounded { lat, lng }. Draws nothing. */
function ClickHandler({ onClick }: { onClick?: (p: LatLng) => void }) {
  useMapEvents({
    click(event) {
      if (onClick) {
        onClick({ lat: round5(event.latlng.lat), lng: round5(event.latlng.lng) });
      }
    },
  });
  return null;
}

/** The first time vehicles appear, zoom the map so all of them are visible. Draws nothing. */
function FitToVehiclesOnce({ vehicles }: { vehicles: Vehicle[] }) {
  const map = useMap();
  const done = useRef(false); // a ref survives re-renders, so this happens only once

  useEffect(() => {
    if (done.current || vehicles.length === 0) {
      return;
    }
    done.current = true;

    const points: [number, number][] = [];
    for (const vehicle of vehicles) {
      points.push([vehicle.currentLatitude!, vehicle.currentLongitude!]);
    }
    const bounds = L.latLngBounds(points);
    map.fitBounds(bounds.pad(0.3), { maxZoom: 14 }); // 30% margin, not closer than zoom 14
  }, [vehicles, map]);

  return null;
}

/** Keeps the map centred on a moving point (the driver during a trip). Draws nothing. */
function Follow({ point }: { point: LatLng }) {
  const map = useMap();
  useEffect(() => {
    map.panTo([point.lat, point.lng], { animate: true });
  }, [point.lat, point.lng, map]);
  return null;
}
