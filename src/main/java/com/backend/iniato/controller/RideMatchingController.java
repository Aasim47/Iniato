package com.backend.iniato.controller;

import com.backend.iniato.dto.NearbyDriverDTO;
import com.backend.iniato.dto.RideMatchResponseDTO;
import com.backend.iniato.dto.RideRequestDTO;
import com.backend.iniato.dto.RideRequestResponseDTO;
import com.backend.iniato.entity.User;
import com.backend.iniato.services.RideMatchingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/matching")
public class RideMatchingController {

    private final RideMatchingService rideMatchingService;

    @Autowired
    public RideMatchingController(RideMatchingService rideMatchingService) {
        this.rideMatchingService = rideMatchingService;
    }

    @PostMapping("/request")
    public ResponseEntity<RideRequestResponseDTO> createRideRequest(
            @RequestBody RideRequestDTO requestDTO,
            @AuthenticationPrincipal User passenger) {
        return ResponseEntity.ok(rideMatchingService.saveRideRequest(requestDTO, passenger));
    }

    // 🔍 Find other passengers or available shared rides nearby
    @PostMapping("/find")
    public ResponseEntity<RideMatchResponseDTO> findSharedRideMatches(@RequestBody RideRequestDTO requestDTO) {
        RideMatchResponseDTO response = rideMatchingService.findSharedRideMatches(requestDTO);
        return ResponseEntity.ok(response);
    }
}
