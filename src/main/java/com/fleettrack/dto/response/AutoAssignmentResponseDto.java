package com.fleettrack.dto.response;

/** Result of auto-assignment: the updated delivery and how far the chosen vehicle is from pickup. */
public record AutoAssignmentResponseDto(DeliveryResponseDto delivery, double distanceKm) {
}
