package com.backend.iniato.security;

import com.backend.iniato.dto.*;
import com.backend.iniato.entity.DriverProfile;
import com.backend.iniato.entity.PassengerProfile;
import com.backend.iniato.entity.User;
import com.backend.iniato.repo.DriverProfileRepository;
import com.backend.iniato.repo.PassengerProfileRepository;
import com.backend.iniato.repo.UserRepository;
import com.backend.iniato.services.CustomUserDetailsService;
import com.backend.iniato.services.OtpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private OtpService otpService;
    @Autowired private DriverProfileRepository driverProfileRepository;
    @Autowired private PassengerProfileRepository passengerProfileRepository;
    @Autowired private CustomUserDetailsService userDetailsService;

    // ─── OTP ───

    public void sendOtp(String phoneNumber) { otpService.sendOtp(phoneNumber); }
    public boolean verifyOtp(String phoneNumber, String otp) { return otpService.verifyOtp(phoneNumber, otp); }

    // ─── Driver Registration ───

    public AuthResponse registerDriver(DriverRegisterRequest request, String phoneNumber) {
        if (userRepository.findByPhoneNumber(phoneNumber).isPresent())
            throw new IllegalArgumentException("Phone number already in use");
        if (!otpService.isPhoneVerified(phoneNumber))
            throw new IllegalStateException("Phone number not verified. Please verify OTP first.");

        var user = User.builder().phoneNumber(phoneNumber).userType(User.UserType.DRIVER).build();
        userRepository.save(user);

        driverProfileRepository.save(DriverProfile.builder()
                .phoneNumber(phoneNumber).licenseNumber(request.getLicenseNumber())
                .vehicleRegistration(request.getVehicleDetails())
                .fullName(request.getName()).user(user).build());

        otpService.clearVerification(phoneNumber);
        return buildAuthResponse(user);
    }

    // ─── Rider Registration ───

    public AuthResponse registerRider(RiderRegisterRequest request, String phoneNumber) {
        if (userRepository.findByPhoneNumber(phoneNumber).isPresent())
            throw new IllegalArgumentException("Phone number already in use");
        if (!otpService.isPhoneVerified(phoneNumber))
            throw new IllegalStateException("Phone number not verified. Please verify OTP first.");

        var user = User.builder().phoneNumber(phoneNumber).userType(User.UserType.PASSENGER).build();
        userRepository.save(user);

        passengerProfileRepository.save(PassengerProfile.builder()
                .fullName(request.getName()).gender(request.getGender()).user(user).build());

        otpService.clearVerification(phoneNumber);
        return buildAuthResponse(user);
    }

    // ─── Login with OTP ───

    public AuthResponse loginWithOtp(String phoneNumber) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new IllegalArgumentException("User not found. Please register first."));
        otpService.clearVerification(phoneNumber);
        return buildAuthResponse(user);
    }

    // ─── Refresh Token ───

    public AuthResponse refresh(String refreshToken) {
        String username = jwtService.validateRefreshToken(refreshToken);
        if (username == null) throw new IllegalStateException("Invalid or expired refresh token");
        var userDetails = userDetailsService.loadUserByUsername(username);
        jwtService.revokeRefreshToken(refreshToken); // rotate
        User user = userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new RuntimeException("User not found"));
        return buildAuthResponse(user);
    }

    // ─── Logout ───

    public void logout(String accessToken, String refreshToken) {
        jwtService.revokeToken(accessToken);
        if (refreshToken != null) jwtService.revokeRefreshToken(refreshToken);
    }

    // ─── Helper ───

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        return AuthResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .phoneNumber(user.getPhoneNumber())
                .userType(user.getUserType() != null ? user.getUserType().name() : null)
                .build();
    }
}