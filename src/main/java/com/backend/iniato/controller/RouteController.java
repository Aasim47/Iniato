package com.backend.iniato.controller;

import com.backend.iniato.dto.*;
import com.backend.iniato.entity.User;
import com.backend.iniato.repo.UserRepository;
import com.backend.iniato.services.RouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
public class RouteController {


    private final RouteService routeService;
    private final UserRepository userRepository;

    @Autowired
    public RouteController(RouteService routeService, UserRepository userRepository) {
        this.routeService = routeService;
        this.userRepository = userRepository;
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByPhoneNumber(username)
                .or(() -> userRepository.findByEmail(username))
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * Driver declares a new route.
     * POST /api/routes
     */
    @PostMapping
    @PreAuthorize("hasAuthority('DRIVER')")
    public ResponseEntity<RouteResponseDTO> createRoute(@RequestBody RouteCreateRequest request) {
        return ResponseEntity.ok(routeService.createRoute(request, getCurrentUser()));
    }

    /**
     * Riders discover active routes near their location.
     * GET /api/routes/nearby?lat=&lng=
     */
    @GetMapping("/nearby")
    @PreAuthorize("hasAuthority('PASSENGER')")
    public ResponseEntity<List<RouteResponseDTO>> getNearbyRoutes(
            @RequestParam double lat,
            @RequestParam double lng) {
        return ResponseEntity.ok(routeService.getNearbyRoutes(lat, lng));
    }

    /**
     * Driver updates their current location on a route.
     * PATCH /api/routes/{id}/update-location
     */
    @PatchMapping("/{id}/update-location")
    @PreAuthorize("hasAuthority('DRIVER')")
    public ResponseEntity<Void> updateLocation(
            @PathVariable Long id,
            @RequestBody RouteUpdateLocationRequest request) {
        routeService.updateLocation(id, request, getCurrentUser());
        return ResponseEntity.ok().build();
    }

    /**
     * Driver adds a pickup/drop stop to their route.
     * PATCH /api/routes/{id}/add-stop
     */
    @PatchMapping("/{id}/add-stop")
    @PreAuthorize("hasAuthority('DRIVER')")
    public ResponseEntity<RouteResponseDTO> addStop(
            @PathVariable Long id,
            @RequestBody RouteAddStopRequest request) {
        return ResponseEntity.ok(routeService.addStop(id, request, getCurrentUser()));
    }

    /**
     * Driver marks their route as completed.
     * PATCH /api/routes/{id}/complete
     */
    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('DRIVER')")
    public ResponseEntity<RouteResponseDTO> completeRoute(@PathVariable Long id) {
        return ResponseEntity.ok(routeService.completeRoute(id, getCurrentUser()));
    }
}
