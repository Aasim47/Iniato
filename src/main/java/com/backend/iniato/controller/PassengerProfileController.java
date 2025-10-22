package com.backend.iniato.controller;

import com.backend.iniato.dto.PassengerProfileRequest;
import com.backend.iniato.dto.PassengerProfileResponse;
import com.backend.iniato.services.PassengerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/passenger")
@RequiredArgsConstructor
public class PassengerProfileController {

    private final PassengerProfileService passengerProfileService;

    @GetMapping("/profile")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<PassengerProfileResponse> getProfile() {
        return ResponseEntity.ok(passengerProfileService.getProfile());
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<PassengerProfileResponse> updateProfile(@RequestBody PassengerProfileRequest request) {
        return ResponseEntity.ok(passengerProfileService.updateProfile(request));
    }
}
