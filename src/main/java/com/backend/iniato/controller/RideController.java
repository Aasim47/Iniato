package com.backend.iniato.controller;

import com.backend.iniato.dto.*;
import com.backend.iniato.entity.RideRequest;
import com.backend.iniato.services.RideService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rides")
public class RideController {

    private final RideService rideService;

    @Autowired
    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    // ─── Passenger endpoints ────────────────────────────────────────────────

    /**
     * Rider requests to join a specific ride (identified by rideId in body).
     * POST /api/rides/request
     */
    @PostMapping("/request")
    @PreAuthorize("hasAuthority('PASSENGER')")
    public ResponseEntity<RideResponseDTO> requestRide(@RequestBody RideRequestDTO requestDTO) {
        return ResponseEntity.ok(rideService.requestSharedRide(requestDTO));
    }

    /**
     * Passenger views all rides they're part of.
     * GET /api/rides/my
     */
    @GetMapping("/my")
    @PreAuthorize("hasAuthority('PASSENGER')")
    public ResponseEntity<List<RideResponseDTO>> getMySharedRides() {
        return ResponseEntity.ok(rideService.getPassengerSharedRides());
    }

    /**
     * Passenger leaves a ride before it starts.
     * POST /api/rides/{rideId}/leave
     */
    @PostMapping("/{rideId}/leave")
    @PreAuthorize("hasAuthority('PASSENGER')")
    public ResponseEntity<RideResponseDTO> leaveSharedRide(@PathVariable Long rideId) {
        return ResponseEntity.ok(rideService.leaveSharedRide(rideId));
    }

    // ─── Driver endpoints ───────────────────────────────────────────────────

    /**
     * Driver views rides in POOL_FORMING status (their active routes).
     * GET /api/rides/available
     */
    @GetMapping("/available")
    @PreAuthorize("hasAuthority('DRIVER')")
    public ResponseEntity<List<RideResponseDTO>> getAvailableSharedRides() {
        return ResponseEntity.ok(rideService.getAvailableSharedRides());
    }

    /**
     * Driver views pending join requests for their ride.
     * GET /api/rides/{rideId}/requests
     */
    @GetMapping("/{rideId}/requests")
    @PreAuthorize("hasAuthority('DRIVER')")
    public ResponseEntity<List<RideRequest>> getPendingRequests(@PathVariable Long rideId) {
        return ResponseEntity.ok(rideService.getPendingRequests(rideId));
    }

    /**
     * Driver accepts a specific passenger's join request.
     * POST /api/rides/{rideId}/requests/{requestId}/accept
     */
    @PostMapping("/{rideId}/requests/{requestId}/accept")
    @PreAuthorize("hasAuthority('DRIVER')")
    public ResponseEntity<RideResponseDTO> acceptPassengerRequest(
            @PathVariable Long rideId,
            @PathVariable Long requestId) {
        return ResponseEntity.ok(rideService.acceptPassengerRequest(rideId, requestId));
    }

    /**
     * Driver starts the ride.
     * POST /api/rides/{rideId}/start
     */
    @PostMapping("/{rideId}/start")
    @PreAuthorize("hasAuthority('DRIVER')")
    public ResponseEntity<RideResponseDTO> startSharedRide(@PathVariable Long rideId) {
        return ResponseEntity.ok(rideService.startSharedRide(rideId));
    }

    /**
     * Driver completes the ride.
     * POST /api/rides/{rideId}/complete
     */
    @PostMapping("/{rideId}/complete")
    @PreAuthorize("hasAuthority('DRIVER')")
    public ResponseEntity<RideResponseDTO> completeSharedRide(@PathVariable Long rideId) {
        return ResponseEntity.ok(rideService.completeSharedRide(rideId));
    }
}
