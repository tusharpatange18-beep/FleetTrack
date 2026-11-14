package com.fleettrack.repository;

import com.fleettrack.entity.Vehicle;
import com.fleettrack.entity.enums.DriverStatus;
import com.fleettrack.entity.enums.VehicleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    boolean existsByRegistrationNumber(String registrationNumber);

    boolean existsByDriverId(Long driverId);

    Optional<Vehicle> findByDriverId(Long driverId);

    List<Vehicle> findByStatus(VehicleStatus status);

    /**
     * SELECT ... FOR UPDATE: holds a row lock until the transaction ends, so two dispatchers
     * cannot both see the vehicle as AVAILABLE and assign it twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.id = :id")
    Optional<Vehicle> findByIdForUpdate(Long id);

    /**
     * Bounding-box prefilter for proximity search: vehicles in the given status with a fresh
     * location inside the box. Uses idx_vehicle_status_lat; exact distance is checked in Java.
     */
    @Query("""
            select new com.fleettrack.repository.VehicleLocationView(
                v.id, v.registrationNumber, v.type, v.currentLatitude, v.currentLongitude,
                v.locationUpdatedAt, d.id)
            from Vehicle v left join v.driver d
            where v.status = :status
              and v.currentLatitude between :minLat and :maxLat
              and v.currentLongitude between :minLon and :maxLon
              and v.locationUpdatedAt >= :freshSince
            """)
    List<VehicleLocationView> findInBox(VehicleStatus status,
                                        double minLat, double maxLat, double minLon, double maxLon,
                                        LocalDateTime freshSince);

    /** Same as {@link #findInBox}, restricted to vehicles whose driver has the given status. */
    @Query("""
            select new com.fleettrack.repository.VehicleLocationView(
                v.id, v.registrationNumber, v.type, v.currentLatitude, v.currentLongitude,
                v.locationUpdatedAt, d.id)
            from Vehicle v join v.driver d
            where v.status = :status
              and d.status = :driverStatus
              and v.currentLatitude between :minLat and :maxLat
              and v.currentLongitude between :minLon and :maxLon
              and v.locationUpdatedAt >= :freshSince
            """)
    List<VehicleLocationView> findWithDriverInBox(VehicleStatus status, DriverStatus driverStatus,
                                                  double minLat, double maxLat, double minLon, double maxLon,
                                                  LocalDateTime freshSince);

    @Query("select new com.fleettrack.repository.StatusCount(v.status, count(v)) from Vehicle v group by v.status")
    List<StatusCount> countGroupedByStatus();
}
