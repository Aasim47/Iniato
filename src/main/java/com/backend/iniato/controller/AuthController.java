package com.backend.iniato.controller;

import com.backend.iniato.dto.*;
import com.backend.iniato.security.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody BaseRegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register/driver/{phoneNumber}")
    public ResponseEntity<AuthResponse> registerdriver(@RequestBody DriverRegisterRequest request, @PathVariable String phoneNumber) {
        AuthResponse response = authService.registerDriver(request,phoneNumber);
        return ResponseEntity.ok(response);
    }


    @PostMapping("/register/driver/verify-otp")
    public ResponseEntity<String> verifyDriverOtp(@RequestBody VerifyOtpRequest request) {
        boolean isValid = authService.verifyOtp(request.getPhoneNumber(), request.getOtp());
        if (isValid) {
            return ResponseEntity.ok("OTP verified. You are now a driver for Iniato. Congratulations");
        } else {
            return ResponseEntity.badRequest().body("Invalid OTP");
        }
    }

    @PostMapping("/register/driver/send-otp")
    public ResponseEntity<String> sendDriverOtp(@RequestBody SendOtpRequest request) {
        authService.sendOtp(request.getPhoneNumber());
        return ResponseEntity.ok("OTP sent successfully");
    }

    @PostMapping("/register/rider/{phoneNumber}")
    public ResponseEntity<AuthResponse> registerrider(@RequestBody RiderRegisterRequest request, @PathVariable String phoneNumber) {
        AuthResponse response = authService.registerRider(request,phoneNumber);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login/driver/{phoneNumber}")
    public ResponseEntity<AuthResponse> loginDriver(@RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login/rider/{phoneNumber}")
    public ResponseEntity<AuthResponse> loginRider(@RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
