package com.fleettrack.repository;

import com.fleettrack.entity.Delivery;
import com.fleettrack.entity.enums.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    boolean existsByVehicleIdAndStatusIn(Long vehicleId, Collection<DeliveryStatus> statuses);
}
