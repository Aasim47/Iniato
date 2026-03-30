package com.backend.iniato.controller;

import com.backend.iniato.dto.RiderCreateRequest;
import com.backend.iniato.entity.Route;
import com.backend.iniato.services.MatchingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rider")
public class RiderController {

    private final MatchingService matchingService;

    @Autowired
    public RiderController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    /**
     * Match rider's request to a nearby active route.
     * POST /rider/create-request
     */
    @PostMapping("/create-request")
    public ResponseEntity<?> createRequest(@RequestBody RiderCreateRequest request) {
        var match = matchingService.matchPassengerToRoute(request);
        if (match.isPresent()) {
            return ResponseEntity.ok(match.get());
        } else {
            return ResponseEntity.status(404).body("No compatible route found.");
        }
    }

    /**
     * Fetch active routes near a location.
     * GET /rider/nearby-routes?lat=&lng=
     */
    @GetMapping("/nearby-routes")
    public ResponseEntity<List<Route>> getNearbyRoutes(
            @RequestParam double lat,
            @RequestParam double lng) {
        List<Route> routes = matchingService.fetchActiveRoutesNearby(lat, lng, 5.0);
        return ResponseEntity.ok(routes);
    }
}
