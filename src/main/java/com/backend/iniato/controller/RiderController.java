package com.backend.iniato.controller;

import com.backend.iniato.dto.RiderBookRouteRequest;
import com.backend.iniato.dto.RiderCreateRequest;
import com.backend.iniato.entity.Booking;
import com.backend.iniato.entity.Route;
import com.backend.iniato.services.MatchingService;
import com.backend.iniato.services.RideService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rider")
public class RiderController {
    private final MatchingService matchingService;
    private final RideService rideService;

    public RiderController(MatchingService matchingService, RideService rideService) {
        this.matchingService = matchingService;
        this.rideService = rideService;
    }

    @PostMapping("/create-request")
    public ResponseEntity<?> createRequest(@RequestBody RiderCreateRequest request) {
        // Match rider to best route
        var match = matchingService.matchPassengerToRoute(request);
        if (match.isPresent()) {
            // Optionally auto-book or suggest to rider
            return ResponseEntity.ok(match.get());
        } else {
            return ResponseEntity.status(404).body("No compatible route found. Rider added to demand pool.");
        }
    }

    @GetMapping("/nearby-routes")
    public ResponseEntity<List<Route>> getNearbyRoutes(@RequestParam double lat, @RequestParam double lng) {
        // Fetch active routes near rider
        List<Route> routes = matchingService.fetchActiveRoutesNearby(lat, lng, 5.0);
        return ResponseEntity.ok(routes);
    }

    @PostMapping("/book-route")
    public ResponseEntity<Booking> bookRoute(@RequestBody RiderBookRouteRequest request) {
        // Book rider to route
        Booking booking = rideService.bookRoute(request);
        return ResponseEntity.ok(booking);
    }

    @PostMapping("/cancel")
    public ResponseEntity<?> cancel(@RequestParam String bookingId) {
        // Cancel booking
        boolean cancelled = rideService.cancelBooking(bookingId);
        if (cancelled) {
            return ResponseEntity.ok("Booking cancelled.");
        } else {
            return ResponseEntity.status(400).body("Unable to cancel booking.");
        }
    }
}
