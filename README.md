# FleetTrack

FleetTrack is a comprehensive backend system for managing a logistics fleet. It provides RESTful APIs to manage vehicles, drivers, customers, and deliveries, including real-time location tracking and intelligent delivery assignment.

## Features

- **Vehicle Management**: Track vehicle types, status, and driver assignments.
- **Driver Management**: Register drivers and handle their active/inactive status.
- **Delivery Management**: Robust state machine for delivery statuses (CREATED -> ASSIGNED -> PICKED_UP -> IN_TRANSIT -> DELIVERED).
- **Proximity Search**: Find nearby available vehicles based on real-time location using the Haversine formula.
- **Location History**: Append-only audit trail of all vehicle locations over time.
- **Security**: Stateless JWT-based authentication and role-based access control (ADMIN, DISPATCHER).

## Architecture

```
Client / Swagger / Postman
         |
         v
  Spring Boot API
         |
+--------+--------+
|                 |
Security      Controllers
JWT/RBAC          |
         v
      Services
         |
+-----------+-----------+
|                       |
Business Logic     Haversine
         |
         v
    Repositories
         |
         v
  Spring Data JPA
         |
         v
       MySQL
```

## Tech Stack

- **Java 21**
- **Spring Boot 3.3.0** (Web, Data JPA, Security, Validation)
- **MySQL 8** (Database)
- **Flyway** (Database Migrations)
- **JWT** (Authentication)
- **Maven** (Build Tool)
- **JUnit 5 / Mockito** (Testing)
- **Docker & Docker Compose** (Containerization)

## Database Schema

- **AppUser**: Manages login credentials and roles.
- **Driver**: Driver information (name, phone, license, status).
- **Vehicle**: Fleet assets (type, status, current location, driver assignment).
- **Customer**: Customer details (name, phone, pickup/drop coordinates).
- **Delivery**: Delivery orders linking Customer, Vehicle, Driver, and status.
- **LocationHistory**: Historical audit of vehicle coordinates and timestamps.

## API Documentation

Swagger UI is available locally at:
`http://localhost:8080/swagger-ui.html`

## Location Algorithm

The system uses the **Haversine formula** to calculate the great-circle distance between two points on a sphere given their longitudes and latitudes. The `HaversineCalculator` runs in-memory to filter and sort candidate vehicles by proximity without requiring complex PostGIS infrastructure.

## Business Rules

- **Delivery State Machine**: Prevents invalid transitions. Deliveries must follow: CREATED -> ASSIGNED -> PICKED_UP -> IN_TRANSIT -> DELIVERED.
- **Assignments**: A vehicle cannot be assigned to a new delivery if it is not `AVAILABLE` or lacks an `ACTIVE` driver.
- **Location Updates**: Updating a vehicle's location automatically records a point-in-time snapshot in `LocationHistory`.
- **Registration**: New accounts default to `DISPATCHER`. An `ADMIN` account can only be created by an authenticated admin, except the very first account, which bootstraps the system.

## API Endpoints

All endpoints except `/api/auth/**` and Swagger require an `Authorization: Bearer <token>` header.

| Method | Endpoint | Access |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | Public |
| GET, POST | `/api/vehicles` | ADMIN, DISPATCHER |
| GET, PATCH, DELETE | `/api/vehicles/{id}` | ADMIN, DISPATCHER |
| PATCH | `/api/vehicles/{id}/location` | ADMIN, DISPATCHER |
| GET | `/api/vehicles/nearby?latitude=&longitude=&radiusKm=10` | ADMIN, DISPATCHER |
| GET, POST | `/api/drivers` | ADMIN |
| GET, PATCH, DELETE | `/api/drivers/{id}` | ADMIN |
| PATCH | `/api/drivers/{driverId}/assign-vehicle/{vehicleId}` | ADMIN |
| GET, POST | `/api/deliveries` | ADMIN, DISPATCHER |
| GET | `/api/deliveries/{id}` | ADMIN, DISPATCHER |
| PATCH | `/api/deliveries/{id}/assign`, `/api/deliveries/{id}/status` | ADMIN, DISPATCHER |
| GET, POST | `/api/customers`, GET `/api/customers/{id}` | Any authenticated user |

## Environment Variables

The application has **no built-in defaults** for credentials; every variable below must be set. Copy `local.env.example` to `local.env` (git-ignored) and fill in your values.

| Variable | Used by | Example |
|---|---|---|
| `DB_URL` | App | `jdbc:mysql://localhost:3306/fleettrack?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true` |
| `DB_USERNAME` | App | `fleettrack_user` |
| `DB_PASSWORD` | App | your database password |
| `JWT_SECRET` | App | Base64-encoded secret of at least 256 bits (`openssl rand -base64 32`) |
| `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` | docker-compose (MySQL) | match `DB_USERNAME` / `DB_PASSWORD` |

## Running Locally

1. Install Java 21 and Docker (Maven is optional; the wrapper `mvnw` is included).
2. Create your env file:
   ```bash
   cp local.env.example local.env   # then edit the values
   ```
3. Start MySQL:
   ```bash
   docker compose --env-file local.env up -d
   ```
4. Export the variables from `local.env` and run the app (Flyway creates the schema on first start):
   ```bash
   # bash
   export $(grep -v '^#' local.env | xargs)
   ./mvnw spring-boot:run

   # PowerShell
   Get-Content local.env | Where-Object { $_ -and $_ -notmatch '^#' } | ForEach-Object { $k, $v = $_ -split '=', 2; Set-Item "env:$k" $v }
   .\mvnw.cmd spring-boot:run
   ```
5. Open Swagger UI at `http://localhost:8080/swagger-ui.html`, register a user, log in, and use the returned token.

## Docker

The `Dockerfile` packages the built JAR; `docker-compose.yml` currently runs only the MySQL database.
```bash
./mvnw clean package -DskipTests
docker build -t fleettrack-app .
docker run --env-file local.env -p 8080:8080 fleettrack-app
```
When the app runs in a container, point `DB_URL` at the database host reachable from the container (for example `host.docker.internal`).

## Testing

Unit tests use JUnit 5 and Mockito (no database needed):
```bash
./mvnw clean test
```
They cover the Haversine calculator, the nearby-vehicle search (radius filtering, availability filtering, sorting by distance) and the registration role rules.

## Future Scalability

- **Redis**: For caching frequently accessed data or holding ephemeral location streams.
- **Kafka**: Event-driven architecture for real-time location stream processing.
- **Geospatial Indexing**: PostGIS integration for querying millions of vehicles.
- **Microservices**: Decoupling deliveries, users, and vehicles into distinct services.
