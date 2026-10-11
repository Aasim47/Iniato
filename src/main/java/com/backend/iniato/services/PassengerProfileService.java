package com.backend.iniato.services;


import com.backend.iniato.dto.PassengerProfileRequest;
import com.backend.iniato.dto.PassengerProfileResponse;
import com.backend.iniato.entity.PassengerProfile;
import com.backend.iniato.entity.User;
import com.backend.iniato.repo.PassengerProfileRepository;
import com.backend.iniato.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PassengerProfileService {

    private final PassengerProfileRepository passengerProfileRepository;
    private final UserRepository userRepository;
    private final UserLookupService userLookupService;

    public PassengerProfileService(PassengerProfileRepository passengerProfileRepository,
                                   UserRepository userRepository,
                                   UserLookupService userLookupService) {
        this.passengerProfileRepository = passengerProfileRepository;
        this.userRepository = userRepository;
        this.userLookupService = userLookupService;
    }

    @Transactional(readOnly = true)
    public PassengerProfileResponse getProfile() {
        User user = userLookupService.currentUser();
        PassengerProfile profile = passengerProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        return toResponse(user, profile);
    }

    @Transactional
    public PassengerProfileResponse updateProfile(PassengerProfileRequest request) {
        User current = userLookupService.currentUser();

        // The phone number is cached as a lookup key, so the OLD value has to be
        // evicted — capture it before anything changes it.
        final String previousEmail = current.getEmail();
        final String previousPhone = current.getPhoneNumber();

        PassengerProfile profile = passengerProfileRepository.findByUser(current)
                .orElse(PassengerProfile.builder().user(current).build());
        profile.setFullName(request.getFullName());

        User user = current;
        boolean phoneChanged = request.getPhoneNumber() != null
                && !request.getPhoneNumber().isBlank()
                && !request.getPhoneNumber().equals(previousPhone);

        if (phoneChanged) {
            // currentUser() may be a detached snapshot rebuilt from cache, and
            // saving that would merge its detached Roles too. Load the managed
            // entity before mutating.
            user = userLookupService.loadManaged(current.getId());
            user.setPhoneNumber(request.getPhoneNumber());
            userRepository.save(user);
        }

        passengerProfileRepository.save(profile);

        if (phoneChanged) {
            // Evict both the old identifier (or the next login would resolve a
            // stale user) and the new one (in case it was negatively resolved
            // moments ago).
            userLookupService.evict(previousEmail, previousPhone);
            userLookupService.evict(user.getEmail(), user.getPhoneNumber());
        }

        return toResponse(user, profile);
    }

    private PassengerProfileResponse toResponse(User user, PassengerProfile profile) {
        return PassengerProfileResponse.builder()
                .email(user.getEmail())
                .fullName(profile.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .gender(profile.getGender() != null ? profile.getGender().name() : null)
                .build();
    }
}
