// Mirrors the FleetTrack backend DTOs.

export type VehicleStatus = 'AVAILABLE' | 'BUSY' | 'OFFLINE' | 'INACTIVE';
export type VehicleType = 'BIKE' | 'VAN' | 'TRUCK';
export type DeliveryStatus = 'CREATED' | 'ASSIGNED' | 'PICKED_UP' | 'IN_TRANSIT' | 'DELIVERED' | 'CANCELLED';
export type DriverStatus = 'ACTIVE' | 'INACTIVE';

export interface LatLng {
  lat: number;
  lng: number;
}

export interface Vehicle {
  id: number;
  registrationNumber: string;
  type: VehicleType;
  status: VehicleStatus;
  currentLatitude: number | null;
  currentLongitude: number | null;
  locationUpdatedAt: string | null;
  driverId: number | null;
}

export interface Driver {
  id: number;
  name: string;
  phone: string;
  licenseNumber: string;
  status: DriverStatus;
}

export interface Customer {
  id: number;
  name: string;
  phone: string;
  pickupLatitude: number;
  pickupLongitude: number;
  dropLatitude: number;
  dropLongitude: number;
}

export interface Delivery {
  id: number;
  vehicleId: number | null;
  driverId: number | null;
  customerId: number;
  status: DeliveryStatus;
  pickupLatitude: number | null;
  pickupLongitude: number | null;
  dropLatitude: number | null;
  dropLongitude: number | null;
}

export interface NearbyVehicle {
  vehicleId: number;
  registrationNumber: string;
  type: VehicleType;
  distanceKm: number;
  driverId: number | null;
  locationUpdatedAt: string | null;
}

export interface AutoAssignment {
  delivery: Delivery;
  distanceKm: number;
}

export interface DashboardSummary {
  vehiclesByStatus: Record<VehicleStatus, number>;
  deliveriesByStatus: Record<DeliveryStatus, number>;
  activeDeliveries: number;
  activeDrivers: number;
}

export interface Page<T> {
  content: T[];
}

export const ACTIVE_DELIVERY_STATUSES: DeliveryStatus[] = ['ASSIGNED', 'PICKED_UP', 'IN_TRANSIT'];
