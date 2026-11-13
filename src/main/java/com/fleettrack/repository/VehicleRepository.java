package com.fleettrack.repository;

import com.fleettrack.entity.Vehicle;
import com.fleettrack.entity.enums.VehicleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
}
