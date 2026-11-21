package com.fleettrack.dto.response;

import java.time.LocalDateTime;

public record LocationHistoryResponseDto(Long id, Double latitude, Double longitude, LocalDateTime recordedAt) {
}
