package com.backend.iniato.services;


import com.backend.iniato.dto.PassengerProfileRequest;
import com.backend.iniato.dto.PassengerProfileResponse;
import com.backend.iniato.entity.PassengerProfile;
import com.backend.iniato.entity.User;
import com.backend.iniato.repo.PassengerProfileRepository;
import com.backend.iniato.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PassengerProfileService {

    private final PassengerProfileRepository passengerProfileRepository;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public PassengerProfileResponse getProfile() {
        User user = getCurrentUser();
        PassengerProfile profile = passengerProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        return PassengerProfileResponse.builder()
                .email(user.getEmail())
                .fullName(profile.getFullName())
                .phoneNumber(profile.getPhoneNumber())
                .build();
    }

    public PassengerProfileResponse updateProfile(PassengerProfileRequest request) {
        User user = getCurrentUser();
        PassengerProfile profile = passengerProfileRepository.findByUser(user)
                .orElse(PassengerProfile.builder().user(user).build());

        profile.setFullName(request.getFullName());
        profile.setPhoneNumber(request.getPhoneNumber());

        passengerProfileRepository.save(profile);

        return PassengerProfileResponse.builder()
                .email(user.getEmail())
                .fullName(profile.getFullName())
                .phoneNumber(profile.getPhoneNumber())
                .build();
    }
}
