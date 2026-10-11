package com.backend.iniato.controller;

import com.backend.iniato.dto.DirectRideRequestDTO;
import com.backend.iniato.dto.DirectRideResponseDTO;
import com.backend.iniato.dto.FareEstimateResponseDTO;
import com.backend.iniato.entity.User;
import com.backend.iniato.services.DirectRideService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/direct-rides")
public class RiderDirectController {

    private final DirectRideService directRideService;

    @Autowired
    public RiderDirectController(DirectRideService directRideService) {
        this.directRideService = directRideService;
    }

    @PostMapping("/request")
    public ResponseEntity<DirectRideResponseDTO> requestDirectRide(
            @RequestBody DirectRideRequestDTO request,
            @AuthenticationPrincipal User passenger) {
        DirectRideResponseDTO response = directRideService.createDirectRide(request, passenger);
        return ResponseEntity.ok(response);
    }

    /**
     * Bare fare, as a JSON number.
     * <p>
     * Kept for app builds that parse the response as a plain number; new
     * callers should use {@code /estimate/detailed}, which carries the fee
     * breakdown too.
     */
    @PostMapping("/estimate")
    public ResponseEntity<Double> estimateFare(@RequestBody DirectRideRequestDTO request) {
        Double estimate = directRideService.estimateDirectRideFare(request);
        return ResponseEntity.ok(estimate);
    }

    /** Fare plus the convenience fee carved out of it. */
    @PostMapping("/estimate/detailed")
    public ResponseEntity<FareEstimateResponseDTO> estimateFareDetailed(
            @RequestBody DirectRideRequestDTO request) {
        return ResponseEntity.ok(directRideService.estimateDirectRideFareDetailed(request));
    }

    @PostMapping("/{rideId}/cancel")
    public ResponseEntity<DirectRideResponseDTO> cancelDirectRide(
            @PathVariable Long rideId,
            @AuthenticationPrincipal User passenger) {
        DirectRideResponseDTO response = directRideService.cancelDirectRideByPassenger(rideId, passenger);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-rides")
    public ResponseEntity<List<DirectRideResponseDTO>> getMyRides(
            @AuthenticationPrincipal User passenger) {
        List<DirectRideResponseDTO> rides = directRideService.getPassengerRides(passenger);
        return ResponseEntity.ok(rides);
    }

    // Single-ride fetch so the rider's active-ride screen can poll one ride's
    // status O(1) instead of downloading the whole history each tick.
    @GetMapping("/{rideId}")
    public ResponseEntity<DirectRideResponseDTO> getRide(
            @PathVariable Long rideId,
            @AuthenticationPrincipal User passenger) {
        return ResponseEntity.ok(directRideService.getPassengerRideById(rideId, passenger));
    }

    @GetMapping("/nearby")
    public ResponseEntity<List<DirectRideResponseDTO>> getNearbyRides(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "2000") double radius) {
        List<DirectRideResponseDTO> rides = directRideService.getNearbyAvailableRides(lat, lng, radius);
        return ResponseEntity.ok(rides);
    }
}