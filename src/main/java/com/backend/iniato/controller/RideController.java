package com.backend.iniato.controller;

import com.backend.iniato.dto.*;
import com.backend.iniato.services.RideService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
public class RideController {

    private final RideService rideService;

    @PostMapping("/request")
    @PreAuthorize("hasAuthority('PASSENGER')")
    public ResponseEntity<RideResponseDTO> requestRide(@RequestBody RideRequestDTO requestDTO) {
        return ResponseEntity.ok(rideService.requestSharedRide(requestDTO));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAuthority('PASSENGER')")
    public ResponseEntity<List<RideResponseDTO>> getMySharedRides() {
        return ResponseEntity.ok(rideService.getPassengerSharedRides());
    }

    @PostMapping("/{rideId}/leave")
    @PreAuthorize("hasAuthority('PASSENGER')")
    public ResponseEntity<RideResponseDTO> leaveSharedRide(@PathVariable Long rideId) {
        return ResponseEntity.ok(rideService.leaveSharedRide(rideId));
    }

    // 🚕 Driver views nearby pooled ride requests that match their route
    @GetMapping("/available")
    @PreAuthorize("hasAuthority('DRIVER')")
    public ResponseEntity<List<RideResponseDTO>> getAvailableSharedRides() {
        return ResponseEntity.ok(rideService.getAvailableSharedRides());
    }

    // ✅ Driver accepts to host a pooled ride
    @PostMapping("/{rideId}/accept")
    @PreAuthorize("hasAuthority('DRIVER')")
    public ResponseEntity<RideResponseDTO> acceptSharedRide(@PathVariable Long rideId) {
        return ResponseEntity.ok(rideService.acceptSharedRide(rideId));
    }

    // ▶️ Driver starts the pooled ride (once minimum passengers are onboard)
    @PostMapping("/{rideId}/start")
    @PreAuthorize("hasAuthority('DRIVER')")
    public ResponseEntity<RideResponseDTO> startSharedRide(@PathVariable Long rideId) {
        return ResponseEntity.ok(rideService.startSharedRide(rideId));
    }

    // 🏁 Driver completes the shared ride (fare split occurs automatically)
    @PostMapping("/{rideId}/complete")
    @PreAuthorize("hasAuthority('DRIVER')")
    public ResponseEntity<RideResponseDTO> completeSharedRide(@PathVariable Long rideId) {
        return ResponseEntity.ok(rideService.completeSharedRide(rideId));
    }
}
