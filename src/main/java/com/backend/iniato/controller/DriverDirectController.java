package com.backend.iniato.controller;

import com.backend.iniato.dto.DirectRideResponseDTO;
import com.backend.iniato.entity.User;
import com.backend.iniato.services.DirectRideService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/driver/direct-rides")
public class DriverDirectController {

    private final DirectRideService directRideService;

    @Autowired
    public DriverDirectController(DirectRideService directRideService) {
        this.directRideService = directRideService;
    }

    @GetMapping("/available")
    public ResponseEntity<List<DirectRideResponseDTO>> getAvailableRides(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "2000") double radius) {
        List<DirectRideResponseDTO> rides = directRideService.getNearbyAvailableRides(lat, lng, radius);
        return ResponseEntity.ok(rides);
    }

    @PostMapping("/{rideId}/cancel")
    public ResponseEntity<DirectRideResponseDTO> cancelDirectRide(
            @PathVariable Long rideId,
            @AuthenticationPrincipal User driver) {
        DirectRideResponseDTO response = directRideService.cancelDirectRideByDriver(rideId, driver);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{rideId}/accept")
    public ResponseEntity<DirectRideResponseDTO> acceptDirectRide(
            @PathVariable Long rideId,
            @AuthenticationPrincipal User driver) {
        DirectRideResponseDTO response = directRideService.acceptDirectRide(rideId, driver);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{rideId}/decline")
    public ResponseEntity<DirectRideResponseDTO> declineDirectRide(
            @PathVariable Long rideId,
            @AuthenticationPrincipal User driver) {
        DirectRideResponseDTO response = directRideService.declineDirectRide(rideId, driver);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{rideId}/start")
    public ResponseEntity<DirectRideResponseDTO> startDirectRide(
            @PathVariable Long rideId,
            @AuthenticationPrincipal User driver) {
        DirectRideResponseDTO response = directRideService.startDirectRide(rideId, driver);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{rideId}/complete")
    public ResponseEntity<DirectRideResponseDTO> completeDirectRide(
            @PathVariable Long rideId,
            @AuthenticationPrincipal User driver) {
        DirectRideResponseDTO response = directRideService.completeDirectRide(rideId, driver);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-rides")
    public ResponseEntity<List<DirectRideResponseDTO>> getMyRides(
            @AuthenticationPrincipal User driver) {
        List<DirectRideResponseDTO> rides = directRideService.getDriverRides(driver);
        return ResponseEntity.ok(rides);
    }
}