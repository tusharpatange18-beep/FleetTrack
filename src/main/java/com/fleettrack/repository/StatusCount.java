package com.fleettrack.repository;

/** One row of a "GROUP BY status" count query. */
public record StatusCount(Enum<?> status, Long count) {
}
