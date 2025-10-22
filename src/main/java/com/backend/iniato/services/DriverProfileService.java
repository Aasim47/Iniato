package com.backend.iniato.services;


import com.backend.iniato.dto.DriverProfileRequest;
import com.backend.iniato.dto.DriverProfileResponse;
import com.backend.iniato.entity.DriverProfile;
import com.backend.iniato.entity.User;
import com.backend.iniato.enums.DriverStatus;
import com.backend.iniato.repo.DriverProfileRepository;
import com.backend.iniato.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DriverProfileService {

    private final DriverProfileRepository driverProfileRepository;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public DriverProfileResponse getProfile() {
        User user = getCurrentUser();
        DriverProfile profile = driverProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        return DriverProfileResponse.builder()
                .email(user.getEmail())
                .fullName(profile.getFullName())
                .phoneNumber(profile.getPhoneNumber())
                .vehicleRegistration(profile.getVehicleRegistration())
                .licenseNumber(profile.getLicenseNumber())
                .status(profile.getStatus())
                .build();
    }

    public DriverProfileResponse updateProfile(DriverProfileRequest request) {
        User user = getCurrentUser();
        DriverProfile profile = driverProfileRepository.findByUser(user)
                .orElse(DriverProfile.builder().user(user).status(DriverStatus.OFFLINE).build());

        profile.setFullName(request.getFullName());
        profile.setPhoneNumber(request.getPhoneNumber());
        profile.setVehicleRegistration(request.getVehicleRegistration());
        profile.setLicenseNumber(request.getLicenseNumber());

        driverProfileRepository.save(profile);

        return getProfile();
    }

    public DriverProfileResponse updateStatus(DriverStatus status) {
        User user = getCurrentUser();
        DriverProfile profile = driverProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        profile.setStatus(status);
        driverProfileRepository.save(profile);

        return getProfile();
    }
}
