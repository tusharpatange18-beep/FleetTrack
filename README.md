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

## Running Locally

1. Ensure Java 21 and Maven are installed.
2. Ensure Docker is running.
3. Start the MySQL database:
   ```bash
   docker-compose up -d
   ```
4. Run the Spring Boot application:
   ```bash
   mvn spring-boot:run
   ```

## Docker

To run the entire stack (App + DB) via Docker:
```bash
docker build -t fleettrack-app .
# Add an app service to docker-compose.yml and run:
docker-compose up
```

## Testing

Run tests using Maven:
```bash
mvn clean test
```

## Environment Variables

- `DB_URL` (default: jdbc:mysql://localhost:3306/fleettrack?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true)
- `DB_USERNAME` (default: fleettrack_user)
- `DB_PASSWORD` (default: fleettrack_pass)
- `JWT_SECRET` (default: predefined 256-bit hex secret)

## Future Scalability

- **Redis**: For caching frequently accessed data or holding ephemeral location streams.
- **Kafka**: Event-driven architecture for real-time location stream processing.
- **Geospatial Indexing**: PostGIS integration for querying millions of vehicles.
- **Microservices**: Decoupling deliveries, users, and vehicles into distinct services.
