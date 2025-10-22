package com.backend.iniato.controller;


import com.backend.iniato.dto.RideRequestDTO;
import com.backend.iniato.dto.RideResponseDTO;
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

    // 🚖 Passenger books a ride
    @PostMapping("/request")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<RideResponseDTO> requestRide(@RequestBody RideRequestDTO requestDTO) {
        return ResponseEntity.ok(rideService.requestRide(requestDTO));
    }

    // 👤 Passenger views their rides
    @GetMapping("/my")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<List<RideResponseDTO>> getPassengerRides() {
        return ResponseEntity.ok(rideService.getPassengerRides());
    }

    // ❌ Passenger cancels a ride
    @PostMapping("/{rideId}/cancel")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<RideResponseDTO> cancelRide(@PathVariable Long rideId) {
        return ResponseEntity.ok(rideService.cancelRide(rideId));
    }

    // 🚗 Driver views available rides
    @GetMapping("/available")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<List<RideResponseDTO>> getAvailableRides() {
        return ResponseEntity.ok(rideService.getAvailableRides());
    }

    // ✅ Driver accepts ride
    @PostMapping("/{rideId}/accept")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<RideResponseDTO> acceptRide(@PathVariable Long rideId) {
        return ResponseEntity.ok(rideService.acceptRide(rideId));
    }

    // ▶️ Driver starts ride
    @PostMapping("/{rideId}/start")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<RideResponseDTO> startRide(@PathVariable Long rideId) {
        return ResponseEntity.ok(rideService.startRide(rideId));
    }

    // 🏁 Driver completes ride
    @PostMapping("/{rideId}/complete")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<RideResponseDTO> completeRide(@PathVariable Long rideId) {
        return ResponseEntity.ok(rideService.completeRide(rideId));
    }
}
