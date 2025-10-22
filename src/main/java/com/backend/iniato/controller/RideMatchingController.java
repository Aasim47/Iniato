package com.backend.iniato.controller;


import com.backend.iniato.dto.NearbyDriverDTO;
import com.backend.iniato.dto.RideRequestDTO;
import com.backend.iniato.entity.RideRequest;
import com.backend.iniato.entity.User;
import com.backend.iniato.services.RideMatchingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
public class RideMatchingController {

    private final RideMatchingService rideMatchingService;

    @PostMapping("/request")
    public ResponseEntity<RideRequest> requestRide(@RequestBody RideRequestDTO requestDTO,
                                                   @AuthenticationPrincipal User passenger) {
        RideRequest rideRequest = rideMatchingService.saveRideRequest(requestDTO, passenger);
        return ResponseEntity.ok(rideRequest);
    }

    @PostMapping("/match")
    public ResponseEntity<List<NearbyDriverDTO>> findNearbyDrivers(@RequestBody RideRequestDTO requestDTO) {
        List<NearbyDriverDTO> nearbyDrivers = rideMatchingService.findNearbyDrivers(requestDTO);
        return ResponseEntity.ok(nearbyDrivers);
    }
}
