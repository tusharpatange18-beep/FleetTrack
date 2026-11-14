package com.fleettrack.repository;

import com.fleettrack.entity.Driver;
import com.fleettrack.entity.enums.DriverStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {

    boolean existsByPhone(String phone);

    boolean existsByLicenseNumber(String licenseNumber);

    long countByStatus(DriverStatus status);
}
