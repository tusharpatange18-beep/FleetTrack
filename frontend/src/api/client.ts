import type {
  AutoAssignment, Customer, DashboardSummary, Delivery, DeliveryStatus, Driver,
  NearbyVehicle, Page, Vehicle, VehicleStatus, VehicleType,
} from './types';

/** An error response from the backend, carrying its JSON `message`. */
export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

// The logged-in user's JWT. AuthContext sets it after login.
let token: string | null = null;
// What to do when the backend says 401 (AuthContext sets this to "log out")
let onUnauthorized: () => void = () => {};

export function setToken(value: string | null) {
  token = value;
}

/** Called when any request comes back 401 (token missing or expired). */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

/**
 * Sends one HTTP request to the backend and returns the JSON answer.
 * <T> is the type of the answer, e.g. request<Vehicle>(...) returns a Vehicle.
 */
async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  // Headers: the token (if logged in) and the body type (if there is a body)
  const headers: Record<string, string> = {};
  if (token) {
    headers.Authorization = 'Bearer ' + token;
  }
  let bodyText: string | undefined = undefined;
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    bodyText = JSON.stringify(body);
  }

  let response: Response;
  try {
    response = await fetch(path, { method: method, headers: headers, body: bodyText });
  } catch {
    // fetch itself only fails when the server can't be reached at all
    throw new ApiError(0, 'Cannot reach the FleetTrack backend. Is it running on port 8080?');
  }

  // Token expired or invalid: log the user out
  if (response.status === 401 && token) {
    onUnauthorized();
  }

  if (!response.ok) {
    // Use the backend's own error message when the body has one
    let message = response.status + ' ' + response.statusText;
    try {
      const json = await response.json();
      if (json && json.message) {
        message = json.message;
      }
    } catch {
      // body was not JSON; keep the status text
    }
    throw new ApiError(response.status, message);
  }

  // 204 No Content, or an empty body: nothing to read
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  if (!text) {
    return undefined as T;
  }
  return JSON.parse(text) as T;
}

function get<T>(path: string): Promise<T> {
  return request<T>('GET', path);
}

/** POST always sends a body ({} when there is nothing to send). */
function post<T>(path: string, body?: unknown): Promise<T> {
  if (body === undefined) {
    body = {};
  }
  return request<T>('POST', path, body);
}

function patch<T>(path: string, body?: unknown): Promise<T> {
  return request<T>('PATCH', path, body);
}

/** Every backend call the UI makes, in one place. List endpoints return one page; we keep its items. */
export const api = {
  login(username: string, password: string) {
    return post<{ token: string }>('/api/auth/login', { username, password });
  },

  summary() {
    return get<DashboardSummary>('/api/dashboard/summary');
  },

  // ---- vehicles ----
  async vehicles() {
    const page = await get<Page<Vehicle>>('/api/vehicles?size=500&sort=id');
    return page.content;
  },
  createVehicle(registrationNumber: string, type: VehicleType) {
    return post<Vehicle>('/api/vehicles', { registrationNumber, type });
  },
  setVehicleStatus(id: number, status: VehicleStatus) {
    return patch<Vehicle>('/api/vehicles/' + id, { status });
  },
  updateLocation(id: number, latitude: number, longitude: number) {
    return patch<Vehicle>('/api/vehicles/' + id + '/location', { latitude, longitude });
  },
  nearby(latitude: number, longitude: number, radiusKm: number) {
    const query = 'latitude=' + latitude + '&longitude=' + longitude + '&radiusKm=' + radiusKm;
    return get<NearbyVehicle[]>('/api/vehicles/nearby?' + query);
  },

  // ---- drivers and customers ----
  async drivers() {
    const page = await get<Page<Driver>>('/api/drivers?size=500&sort=id');
    return page.content;
  },
  async customers() {
    const page = await get<Page<Customer>>('/api/customers?size=500&sort=id');
    return page.content;
  },
  // Omit<Customer, 'id'> = a Customer without the id (the backend creates the id)
  createCustomer(customer: Omit<Customer, 'id'>) {
    return post<Customer>('/api/customers', customer);
  },

  // ---- deliveries ----
  async deliveries() {
    // Newest 100 first
    const page = await get<Page<Delivery>>('/api/deliveries?size=100&sort=id,desc');
    return page.content;
  },
  createDelivery(customerId: number, pickup: [number, number], drop: [number, number]) {
    return post<Delivery>('/api/deliveries', {
      customerId: customerId,
      pickupLatitude: pickup[0],
      pickupLongitude: pickup[1],
      dropLatitude: drop[0],
      dropLongitude: drop[1],
    });
  },
  assign(deliveryId: number, vehicleId: number) {
    return patch<Delivery>('/api/deliveries/' + deliveryId + '/assign?vehicleId=' + vehicleId);
  },
  autoAssign(deliveryId: number) {
    return post<AutoAssignment>('/api/deliveries/' + deliveryId + '/auto-assign');
  },
  setDeliveryStatus(deliveryId: number, status: DeliveryStatus) {
    return patch<Delivery>('/api/deliveries/' + deliveryId + '/status', { status });
  },
};
