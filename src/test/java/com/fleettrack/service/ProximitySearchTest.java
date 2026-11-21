package com.fleettrack.service;

import com.fleettrack.config.FleetProperties;
import com.fleettrack.entity.enums.DriverStatus;
import com.fleettrack.entity.enums.VehicleStatus;
import com.fleettrack.entity.enums.VehicleType;
import com.fleettrack.repository.VehicleLocationView;
import com.fleettrack.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProximitySearchTest {

    private static final double LAT = 19.0760;
    private static final double LON = 72.8777;
    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    private VehicleRepository repository;
    private ProximitySearch search;

    @BeforeEach
    void setUp() {
        repository = mock(VehicleRepository.class);
        FleetProperties properties = new FleetProperties(Duration.ofMinutes(15), 25, List.of(), 10);
        search = new ProximitySearch(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void sortsNearestFirst_withIdAsTieBreak() {
        givenBoxReturns(view(3L, 19.1100, LON), view(2L, LAT, LON), view(1L, LAT, LON));

        List<ProximitySearch.Candidate> result = search.nearestAvailable(LAT, LON, 20, false);

        assertEquals(List.of(1L, 2L, 3L), result.stream().map(c -> c.vehicle().id()).toList());
        assertTrue(result.get(2).distanceKm() > 3.5 && result.get(2).distanceKm() < 4.0);
    }

    @Test
    void dropsBoxCornersThatAreOutsideTheCircle() {
        // 0.085 degrees north and east is inside the 10 km box but about 12.5 km away
        givenBoxReturns(view(1L, LAT + 0.085, LON + 0.085), view(2L, LAT, LON));

        List<ProximitySearch.Candidate> result = search.nearestAvailable(LAT, LON, 10, false);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).vehicle().id());
    }

    @Test
    void asksTheDatabaseOnlyForFreshPingsInsideTheBox() {
        search.nearestAvailable(LAT, LON, 10, false);

        ArgumentCaptor<Double> minLat = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> maxLat = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<LocalDateTime> freshSince = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).findInBox(eq(VehicleStatus.AVAILABLE), minLat.capture(), maxLat.capture(),
                anyDouble(), anyDouble(), freshSince.capture());

        assertTrue(minLat.getValue() < LAT && maxLat.getValue() > LAT);
        assertEquals(LocalDateTime.of(2026, 11, 13, 9, 45), freshSince.getValue(), "now minus 15 minutes");
        verify(repository, never()).findAll();
    }

    @Test
    void requireActiveDriver_usesTheDriverFilteredQuery() {
        search.nearestAvailable(LAT, LON, 10, true);

        verify(repository).findWithDriverInBox(eq(VehicleStatus.AVAILABLE), eq(DriverStatus.ACTIVE),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), any());
        verify(repository, never()).findInBox(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any());
    }

    private void givenBoxReturns(VehicleLocationView... views) {
        when(repository.findInBox(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(views));
    }

    private static VehicleLocationView view(Long id, double lat, double lon) {
        return new VehicleLocationView(id, "REG-" + id, VehicleType.VAN, lat, lon,
                LocalDateTime.of(2026, 11, 13, 9, 59), 100L + id);
    }
}
