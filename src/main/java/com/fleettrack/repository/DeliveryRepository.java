package com.fleettrack.repository;

import com.fleettrack.entity.Delivery;
import com.fleettrack.entity.enums.DeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    boolean existsByVehicleIdAndStatusIn(Long vehicleId, Collection<DeliveryStatus> statuses);

    /** Row lock so two concurrent status changes on one delivery are applied one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Delivery d where d.id = :id")
    Optional<Delivery> findByIdForUpdate(Long id);

    @Query("select new com.fleettrack.repository.StatusCount(d.status, count(d)) from Delivery d group by d.status")
    List<StatusCount> countGroupedByStatus();
}
