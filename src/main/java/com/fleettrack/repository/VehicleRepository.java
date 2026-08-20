package com.fleettrack.repository;

import com.fleettrack.entity.Vehicle;
import com.fleettrack.entity.enums.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    boolean existsByRegistrationNumber(String registrationNumber);
    boolean existsByDriverId(Long driverId);
    List<Vehicle> findByStatus(VehicleStatus status);
}
