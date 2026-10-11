package com.backend.iniato.repo;

import com.backend.iniato.entity.Route;
import com.backend.iniato.enums.VehicleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the rule the product depends on: a vehicle that is full must not be
 * offered to riders, and must come back the moment a seat frees up.
 * <p>
 * Runs against the real PostGIS database — {@code findActiveRoutesNear} is a
 * native ST_DWithin query and cannot be exercised on an embedded H2. Each test
 * rolls back, so no rows survive.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RouteRepositorySeatFilterTest {

    // Somewhere in Sambalpur.
    private static final double LAT = 21.4669;
    private static final double LNG = 83.9812;
    private static final double RADIUS_M = 800.0;

    @Autowired
    private RouteRepository routeRepository;

    private Route persistRoute(VehicleType type, int availableSeats) {
        Route route = Route.builder()
                .originLat(LAT)
                .originLng(LNG)
                .destinationLat(LAT + 0.04)
                .destinationLng(LNG + 0.01)
                .originAddress("Ainthapali")
                .destinationAddress("Burla")
                .vehicleType(type)
                .totalSeats(type.getCapacity())
                .availableSeats(availableSeats)
                .status("ACTIVE")
                .startTime(LocalDateTime.now())
                .build();
        return routeRepository.saveAndFlush(route);
    }

    private boolean isOffered(Long routeId) {
        return routeRepository.findActiveRoutesNear(LAT, LNG, RADIUS_M)
                .stream()
                .anyMatch(r -> r.getId().equals(routeId));
    }

    @Test
    @DisplayName("A route with a free seat is offered to nearby riders")
    void routeWithSeatsIsOffered() {
        Route auto = persistRoute(VehicleType.AUTO, 3);
        assertTrue(isOffered(auto.getId()));
    }

    @Test
    @DisplayName("A full auto disappears from search, and returns when a seat frees")
    void fullAutoIsHiddenUntilASeatFrees() {
        Route auto = persistRoute(VehicleType.AUTO, 3);
        assertTrue(isOffered(auto.getId()), "should start bookable");

        // Three riders board.
        auto.setAvailableSeats(0);
        routeRepository.saveAndFlush(auto);
        assertFalse(isOffered(auto.getId()),
                "a full auto must not be bookable");

        // One rider is dropped off.
        auto.setAvailableSeats(1);
        routeRepository.saveAndFlush(auto);
        assertTrue(isOffered(auto.getId()),
                "the route must return once someone gets out");
    }

    @Test
    @DisplayName("A bike fills on the first booking")
    void bikeFillsAfterOnePassenger() {
        Route bike = persistRoute(VehicleType.BIKE, 1);
        assertTrue(isOffered(bike.getId()));

        bike.setAvailableSeats(0);
        routeRepository.saveAndFlush(bike);
        assertFalse(isOffered(bike.getId()),
                "a bike carries one rider, so it is full immediately");
    }

    @Test
    @DisplayName("Vehicle type survives a round trip and carries its capacity")
    void vehicleTypePersists() {
        Route cab = persistRoute(VehicleType.CAB, 4);
        Route reloaded = routeRepository.findById(cab.getId()).orElseThrow();

        assertTrue(reloaded.getVehicleType() == VehicleType.CAB);
        assertTrue(reloaded.getTotalSeats() == 4,
                "a cab route must be created with 4 seats");
    }

    @Test
    @DisplayName("Only full routes are filtered — others nearby are unaffected")
    void filterOnlyRemovesTheFullRoute() {
        Route full = persistRoute(VehicleType.AUTO, 0);
        Route open = persistRoute(VehicleType.CAB, 4);

        List<Route> offered = routeRepository.findActiveRoutesNear(LAT, LNG, RADIUS_M);
        List<Long> ids = offered.stream().map(Route::getId).toList();

        assertFalse(ids.contains(full.getId()));
        assertTrue(ids.contains(open.getId()));
    }
}
