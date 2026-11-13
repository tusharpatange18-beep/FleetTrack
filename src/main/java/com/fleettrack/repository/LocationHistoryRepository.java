package com.fleettrack.repository;

import com.fleettrack.entity.LocationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface LocationHistoryRepository extends JpaRepository<LocationHistory, Long> {

    /** Served by the composite index idx_lh_vehicle_recorded (vehicle_id, recorded_at). */
    Page<LocationHistory> findByVehicleIdAndRecordedAtBetweenOrderByRecordedAtDesc(
            Long vehicleId, LocalDateTime from, LocalDateTime to, Pageable pageable);
}
