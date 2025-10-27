package com.backend.iniato.security;

import com.backend.iniato.dto.*;
import com.backend.iniato.entity.DriverProfile;
import com.backend.iniato.entity.PassengerProfile;
import com.backend.iniato.entity.Role;
import com.backend.iniato.repo.DriverProfileRepository;
import com.backend.iniato.repo.PassengerProfileRepository;
import com.backend.iniato.repo.RoleRepository;
import com.backend.iniato.entity.User;
import com.backend.iniato.repo.UserRepository;
import com.backend.iniato.services.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor

public class AuthService {
    @Autowired
    private  UserRepository userRepository;
    @Autowired
    private  PasswordEncoder passwordEncoder;
    @Autowired
    private  JwtService jwtService;
    @Autowired
    private  AuthenticationManager authenticationManager;

    @Autowired
    private OtpService otpService;

    @Autowired
    DriverProfileRepository driverProfileRepository;

    @Autowired
    PassengerProfileRepository passengerProfileRepository;

    @Autowired
    private RoleRepository roleRepository;

    public AuthResponse register(BaseRegisterRequest request) {
        // 1. Check if user already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already in use");
        }


        Set<Role> processedRoles = request.getRoles().stream()
                .map(requestedRole -> roleRepository.findByRoleName(requestedRole.getRoleName())
                        .orElseGet(() -> {
                            // Save new role if not found
                            return roleRepository.save(Role.builder()
                                    .roleName(requestedRole.getRoleName())
                                    .build());
                        }))
                .collect(Collectors.toSet());
        // 2. Create new user
        var user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword())) // Encrypt password
                .roles(processedRoles)
                .build();

        // 3. Save user to DB


        userRepository.save(user);

        Set<Role> roles = user.getRoles();


        for(Role role: roles)
        {
            if(role.getRoleName().equals("DRIVER"))
            {
                DriverProfile driverProfile = new DriverProfile();

            }
        }

        // 4. Generate JWT
        var jwtToken = jwtService.generateToken(user);

        List<String> roleNames = user.getRoles().stream()
                .map(Role::getRoleName)
                .toList();
        // 5. Return AuthResponse
        return AuthResponse.builder()
                .token(jwtToken)
                .email(user.getEmail())
                .roles(roleNames)
                .build();
    }

    public void sendOtp(String phoneNumber) {
        otpService.sendOtp(phoneNumber);
    }


    public boolean verifyOtp(String phoneNumber, String otp) {
        return otpService.verifyOtp(phoneNumber, otp);
    }

    public AuthResponse registerDriver(DriverRegisterRequest request,String phoneNumber) {
        // 1. Check if user already exists
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

        // 3. Save user to DB


        userRepository.save(user);

        var driverprofile = DriverProfile.builder().phoneNumber(phoneNumber).licenseNumber(request.getLicenseNumber()).vehicleRegistration(request.getVehicleDetails()).fullName(request.getName()).user(user).build();
        driverProfileRepository.save(driverprofile);



        // 4. Generate JWT
        var jwtToken = jwtService.generateToken(user);


        return AuthResponse.builder()
                .token(jwtToken)
                .build();
    }


    public AuthResponse registerRider(RiderRegisterRequest request, String phoneNumber) {
        // 1. Check if user already exists
        if (userRepository.findByPhoneNumber(phoneNumber).isPresent()) {
            throw new IllegalArgumentException("Phone number already in use");
        }

        if (!otpService.isPhoneVerified(phoneNumber)) {
            throw new IllegalStateException("Phone number not verified. Please verify OTP first.");
        }


        var user = User.builder()
                .phoneNumber(phoneNumber)
                .userType(User.UserType.RIDER)
                .build();

        // 3. Save user to DB


        userRepository.save(user);

        var passengerProfile = PassengerProfile.builder().fullName(request.getName()).gender(request.getGender()).user(user).build();
        passengerProfileRepository.save(passengerProfile);



        // 4. Generate JWT
        var jwtToken = jwtService.generateToken(user);


        return AuthResponse.builder()
                .token(jwtToken)
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        // 1. Authenticate user
        // This will throw AuthenticationException if credentials are bad




        System.out.println("authenticated");
        // 2. If authentication is successful, find user
        Optional<User> user = userRepository.findByEmail(request.getEmail());

            //    .orElseThrow(() -> new IllegalArgumentException("User not found"));
        System.out.println(user.get().getEmail());
        // 3. Generate JWT
        var jwtToken = jwtService.generateToken(user.get());


        List<String> roleNames = new ArrayList<>(user.get().getRoles()).stream()
                .map(Role::getRoleName)
                .toList();


        // 4. Return AuthResponse
        return AuthResponse.builder()
                .token(jwtToken)
                .email(user.get().getEmail())
                .roles(roleNames)
                .build();
    }
}