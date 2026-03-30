package com.backend.iniato.security;

import com.backend.iniato.dto.*;
import com.backend.iniato.entity.DriverProfile;
import com.backend.iniato.entity.PassengerProfile;
import com.backend.iniato.entity.User;
import com.backend.iniato.repo.DriverProfileRepository;
import com.backend.iniato.repo.PassengerProfileRepository;
import com.backend.iniato.repo.UserRepository;
import com.backend.iniato.services.OtpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private OtpService otpService;
    @Autowired
    private DriverProfileRepository driverProfileRepository;
    @Autowired
    private PassengerProfileRepository passengerProfileRepository;

    // ─── OTP send/verify ────────────────────────────────────────────────────

    public void sendOtp(String phoneNumber) {
        otpService.sendOtp(phoneNumber);
    }

    public boolean verifyOtp(String phoneNumber, String otp) {
        return otpService.verifyOtp(phoneNumber, otp);
    }

    // ─── Driver Registration (OTP-verified) ─────────────────────────────────

    public AuthResponse registerDriver(DriverRegisterRequest request, String phoneNumber) {
        if (userRepository.findByPhoneNumber(phoneNumber).isPresent()) {
            throw new IllegalArgumentException("Phone number already in use");
        }

        if (!otpService.isPhoneVerified(phoneNumber)) {
            throw new IllegalStateException("Phone number not verified. Please verify OTP first.");
        }

        var user = User.builder()
                .phoneNumber(phoneNumber)
                .userType(User.UserType.DRIVER)
                .build();

        userRepository.save(user);

        var driverProfile = DriverProfile.builder()
                .phoneNumber(phoneNumber)
                .licenseNumber(request.getLicenseNumber())
                .vehicleRegistration(request.getVehicleDetails())
                .fullName(request.getName())
                .user(user)
                .build();
        driverProfileRepository.save(driverProfile);

        otpService.clearVerification(phoneNumber);

        var jwtToken = jwtService.generateToken(user);
        return AuthResponse.builder()
                .token(jwtToken)
                .phoneNumber(phoneNumber)
                .userType("DRIVER")
                .build();
    }

    // ─── Rider Registration (OTP-verified) ──────────────────────────────────

    public AuthResponse registerRider(RiderRegisterRequest request, String phoneNumber) {
        if (userRepository.findByPhoneNumber(phoneNumber).isPresent()) {
            throw new IllegalArgumentException("Phone number already in use");
        }

        if (!otpService.isPhoneVerified(phoneNumber)) {
            throw new IllegalStateException("Phone number not verified. Please verify OTP first.");
        }

        var user = User.builder()
                .phoneNumber(phoneNumber)
                .userType(User.UserType.PASSENGER)
                .build();

        userRepository.save(user);

        var passengerProfile = PassengerProfile.builder()
                .fullName(request.getName())
                .gender(request.getGender())
                .user(user)
                .build();
        passengerProfileRepository.save(passengerProfile);

        otpService.clearVerification(phoneNumber);

        var jwtToken = jwtService.generateToken(user);
        return AuthResponse.builder()
                .token(jwtToken)
                .phoneNumber(phoneNumber)
                .userType("PASSENGER")
                .build();
    }

    // ─── Login with OTP ─────────────────────────────────────────────────────

    public AuthResponse loginWithOtp(String phoneNumber) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new IllegalArgumentException("User not found. Please register first."));

        otpService.clearVerification(phoneNumber);

        var jwtToken = jwtService.generateToken(user);
        return AuthResponse.builder()
                .token(jwtToken)
                .phoneNumber(user.getPhoneNumber())
                .userType(user.getUserType() != null ? user.getUserType().name() : null)
                .build();
    }

    // ─── Logout ─────────────────────────────────────────────────────────────

    public void logout(String token) {
        jwtService.revokeToken(token);
    }
}