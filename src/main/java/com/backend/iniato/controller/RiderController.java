package com.backend.iniato.controller;

import com.backend.iniato.dto.RiderCreateRequest;
import com.backend.iniato.dto.RouteResponseDTO;
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
        return matchingService.matchPassengerToRouteDto(request)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body("No compatible route found."));
    }

    /**
     * Fetch active routes near a location.
     * GET /rider/nearby-routes?lat=&lng=
     */
    @GetMapping("/nearby-routes")
    public ResponseEntity<List<RouteResponseDTO>> getNearbyRoutes(
            @RequestParam double lat,
            @RequestParam double lng) {
        return ResponseEntity.ok(matchingService.fetchActiveRouteDtosNearby(lat, lng, 5.0));
    }
}
