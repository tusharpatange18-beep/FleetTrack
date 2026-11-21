import type {
  AutoAssignment, Customer, DashboardSummary, Delivery, DeliveryStatus, Driver,
  NearbyVehicle, Page, Vehicle, VehicleStatus, VehicleType,
} from './types';

/** An error response from the backend, carrying its JSON `message`. */
export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
  }
}

let token: string | null = null;
let onUnauthorized: () => void = () => {};

export function setToken(value: string | null) {
  token = value;
}

/** Called when any request comes back 401 (token missing or expired). */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response: Response;
  try {
    response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, 'Cannot reach the FleetTrack backend. Is it running on port 8080?');
  }

  if (response.status === 401 && token) onUnauthorized();
  if (!response.ok) {
    let message = `${response.status} ${response.statusText}`;
    try {
      const json = await response.json();
      if (json?.message) message = json.message;
    } catch {
      // body was not JSON; keep the status text
    }
    throw new ApiError(response.status, message);
  }
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

const get = <T,>(path: string) => request<T>('GET', path);
const post = <T,>(path: string, body?: unknown) => request<T>('POST', path, body ?? {});
const patch = <T,>(path: string, body?: unknown) => request<T>('PATCH', path, body);

export const api = {
  login: (username: string, password: string) =>
    post<{ token: string }>('/api/auth/login', { username, password }),

  summary: () => get<DashboardSummary>('/api/dashboard/summary'),

  vehicles: () => get<Page<Vehicle>>('/api/vehicles?size=500&sort=id').then(p => p.content),
  createVehicle: (registrationNumber: string, type: VehicleType) =>
    post<Vehicle>('/api/vehicles', { registrationNumber, type }),
  setVehicleStatus: (id: number, status: VehicleStatus) => patch<Vehicle>(`/api/vehicles/${id}`, { status }),
  updateLocation: (id: number, latitude: number, longitude: number) =>
    patch<Vehicle>(`/api/vehicles/${id}/location`, { latitude, longitude }),
  nearby: (latitude: number, longitude: number, radiusKm: number) =>
    get<NearbyVehicle[]>(`/api/vehicles/nearby?latitude=${latitude}&longitude=${longitude}&radiusKm=${radiusKm}`),

  drivers: () => get<Page<Driver>>('/api/drivers?size=500&sort=id').then(p => p.content),

  customers: () => get<Page<Customer>>('/api/customers?size=500&sort=id').then(p => p.content),
  createCustomer: (c: Omit<Customer, 'id'>) => post<Customer>('/api/customers', c),

  deliveries: () => get<Page<Delivery>>('/api/deliveries?size=100&sort=id,desc').then(p => p.content),
  createDelivery: (customerId: number, pickup: [number, number], drop: [number, number]) =>
    post<Delivery>('/api/deliveries', {
      customerId,
      pickupLatitude: pickup[0], pickupLongitude: pickup[1],
      dropLatitude: drop[0], dropLongitude: drop[1],
    }),
  assign: (deliveryId: number, vehicleId: number) =>
    patch<Delivery>(`/api/deliveries/${deliveryId}/assign?vehicleId=${vehicleId}`),
  autoAssign: (deliveryId: number) => post<AutoAssignment>(`/api/deliveries/${deliveryId}/auto-assign`),
  setDeliveryStatus: (deliveryId: number, status: DeliveryStatus) =>
    patch<Delivery>(`/api/deliveries/${deliveryId}/status`, { status }),
};
