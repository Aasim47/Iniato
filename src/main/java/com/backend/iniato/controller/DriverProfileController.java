package com.backend.iniato.controller;


import com.backend.iniato.dto.DriverProfileRequest;
import com.backend.iniato.dto.DriverProfileResponse;
import com.backend.iniato.enums.DriverStatus;
import com.backend.iniato.services.DriverProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/driver")
@RequiredArgsConstructor
public class DriverProfileController {

    private final DriverProfileService driverProfileService;

    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('DRIVER')")
    public ResponseEntity<DriverProfileResponse> getProfile() {
        return ResponseEntity.ok(driverProfileService.getProfile());
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DriverProfileResponse> updateProfile(@RequestBody DriverProfileRequest request) {
        return ResponseEntity.ok(driverProfileService.updateProfile(request));
    }

    @PatchMapping("/status")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DriverProfileResponse> updateStatus(@RequestParam DriverStatus status) {
        return ResponseEntity.ok(driverProfileService.updateStatus(status));
    }
}

