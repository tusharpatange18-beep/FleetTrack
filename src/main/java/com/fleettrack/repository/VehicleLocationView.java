package com.fleettrack.repository;

import com.fleettrack.entity.enums.VehicleType;

import java.time.LocalDateTime;

/**
 * Read-only projection used by proximity search. Being a DTO rather than an entity, it does not
 * enter the persistence context, so a later SELECT ... FOR UPDATE on the chosen vehicle always
 * loads its current state instead of a cached copy.
 */
public record VehicleLocationView(
        Long id,
        String registrationNumber,
        VehicleType type,
        Double latitude,
        Double longitude,
        LocalDateTime locationUpdatedAt,
        Long driverId) {
}
