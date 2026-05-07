package com.backend.iniato.controller;

import com.backend.iniato.dto.*;
import com.backend.iniato.security.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    @Autowired
    public AuthController(AuthService authService) { this.authService = authService; }

    // ─── Driver Registration (OTP-based) ────────────────────────────────────

    @PostMapping("/register/driver/send-otp")
    public ResponseEntity<String> sendDriverOtp(@Valid @RequestBody SendOtpRequest request) {
        authService.sendOtp(request.getPhoneNumber());
        return ResponseEntity.ok("OTP sent successfully");
    }

    @PostMapping("/register/driver/verify-otp")
    public ResponseEntity<String> verifyDriverOtp(@Valid @RequestBody VerifyOtpRequest request) {
        boolean isValid = authService.verifyOtp(request.getPhoneNumber(), request.getOtp());
        return isValid ? ResponseEntity.ok("OTP verified. You can now complete registration.")
                       : ResponseEntity.badRequest().body("Invalid OTP");
    }

    @PostMapping("/register/driver/{phoneNumber}")
    public ResponseEntity<AuthResponse> registerDriver(@Valid @RequestBody DriverRegisterRequest request,
                                                        @PathVariable String phoneNumber) {
        return ResponseEntity.ok(authService.registerDriver(request, phoneNumber));
    }

    // ─── Rider Registration (OTP-based) ─────────────────────────────────────

    @PostMapping("/register/rider/send-otp")
    public ResponseEntity<String> sendRiderOtp(@Valid @RequestBody SendOtpRequest request) {
        authService.sendOtp(request.getPhoneNumber());
        return ResponseEntity.ok("OTP sent successfully");
    }

    @PostMapping("/register/rider/verify-otp")
    public ResponseEntity<String> verifyRiderOtp(@Valid @RequestBody VerifyOtpRequest request) {
        boolean isValid = authService.verifyOtp(request.getPhoneNumber(), request.getOtp());
        return isValid ? ResponseEntity.ok("OTP verified. You can now complete registration.")
                       : ResponseEntity.badRequest().body("Invalid OTP");
    }

    @PostMapping("/register/rider/{phoneNumber}")
    public ResponseEntity<AuthResponse> registerRider(@Valid @RequestBody RiderRegisterRequest request,
                                                       @PathVariable String phoneNumber) {
        return ResponseEntity.ok(authService.registerRider(request, phoneNumber));
    }

    // ─── Login (OTP-based only) ─────────────────────────────────────────────

    @PostMapping("/login/send-otp")
    public ResponseEntity<String> sendLoginOtp(@Valid @RequestBody SendOtpRequest request) {
        authService.sendOtp(request.getPhoneNumber());
        return ResponseEntity.ok("OTP sent successfully for login");
    }

    @PostMapping("/login/verify-otp")
    public ResponseEntity<AuthResponse> verifyLoginOtp(@Valid @RequestBody VerifyOtpRequest request) {
        boolean valid = authService.verifyOtp(request.getPhoneNumber(), request.getOtp());
        if (!valid) return ResponseEntity.badRequest().body(null);
        return ResponseEntity.ok(authService.loginWithOtp(request.getPhoneNumber()));
    }

    // ─── Refresh Token ──────────────────────────────────────────────────────

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestParam String refreshToken) {
        return ResponseEntity.ok(authService.refresh(refreshToken));
    }

    // ─── Logout ─────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String refreshToken) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authService.logout(authHeader.substring(7), refreshToken);
        }
        return ResponseEntity.ok("Logged out successfully");
    }
}
