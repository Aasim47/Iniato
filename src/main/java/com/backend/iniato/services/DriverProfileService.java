package com.backend.iniato.services;


import com.backend.iniato.dto.DriverProfileRequest;
import com.backend.iniato.dto.DriverProfileResponse;
import com.backend.iniato.dto.DuesStatusResponse;
import com.backend.iniato.entity.DriverProfile;
import com.backend.iniato.entity.User;
import com.backend.iniato.enums.DriverStatus;
import com.backend.iniato.enums.VehicleType;
import com.backend.iniato.repo.DriverProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverProfileService {

    private final DriverProfileRepository driverProfileRepository;
    private final DriverDuesService driverDuesService;
    private final UserLookupService userLookupService;

    public DriverProfileService(DriverProfileRepository driverProfileRepository,
                                DriverDuesService driverDuesService,
                                UserLookupService userLookupService) {
        this.driverProfileRepository = driverProfileRepository;
        this.driverDuesService = driverDuesService;
        this.userLookupService = userLookupService;
    }

    @Transactional(readOnly = true)
    public DriverProfileResponse getProfile() {
        return buildResponse(userLookupService.currentUser());
    }

    @Transactional
    public DriverProfileResponse updateProfile(DriverProfileRequest request) {
        User user = userLookupService.currentUser();
        DriverProfile profile = driverProfileRepository.findByUser(user)
                .orElse(DriverProfile.builder().user(user).status(DriverStatus.OFFLINE).build());

        profile.setFullName(request.getFullName());
        profile.setPhoneNumber(request.getPhoneNumber());
        profile.setVehicleRegistration(request.getVehicleRegistration());
        profile.setLicenseNumber(request.getLicenseNumber());
        if (request.getVehicleType() != null) {
            // Tolerates legacy values ("AUTO_RICKSHAW", "CAR") sent by older app builds.
            profile.setVehicleType(VehicleType.fromLegacy(request.getVehicleType()));
        }

        driverProfileRepository.save(profile);

        // Build the response from what we already hold. This used to call
        // getProfile(), which re-ran the whole read path — the user lookup and
        // the profile load — immediately after writing it.
        return buildResponse(user, profile);
    }

    @Transactional
    public DriverProfileResponse updateStatus(DriverStatus status) {
        User user = userLookupService.currentUser();
        DriverProfile profile = driverProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        // Going online is taking on new work, so it is gated on dues. Going
        // offline never is — a driver must always be able to stop.
        if (status == DriverStatus.ONLINE) {
            driverDuesService.assertCanDrive(user);
        }

        profile.setStatus(status);
        driverProfileRepository.save(profile);

        return buildResponse(user, profile);
    }

    private DriverProfileResponse buildResponse(User user) {
        DriverProfile profile = driverProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Profile not found"));
        return buildResponse(user, profile);
    }

    /**
     * Assemble the profile payload.
     *
     * <p>Dues come from a single {@link DriverDuesService#statusFor} call. The
     * previous version asked for {@code balanceFor} and {@code isBlocked}
     * separately, and {@code isBlocked} calls {@code balanceFor} internally — so
     * the dues account was loaded twice on every profile read, and four times
     * per update once {@code getProfile()} re-ran. {@code statusFor} loads it
     * once and the blocked flag is derived from the same row.
     */
    private DriverProfileResponse buildResponse(User user, DriverProfile profile) {
        DuesStatusResponse dues = driverDuesService.statusFor(user);

        return DriverProfileResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(profile.getFullName())
                .phoneNumber(profile.getPhoneNumber())
                .vehicleRegistration(profile.getVehicleRegistration())
                .licenseNumber(profile.getLicenseNumber())
                .vehicleType(profile.getVehicleType() != null
                        ? profile.getVehicleType().name() : null)
                .seatCapacity(profile.getVehicleType() != null
                        ? profile.getVehicleType().getCapacity() : null)
                .status(profile.getStatus())
                .averageRating(profile.getAverageRating())
                .ratingCount(profile.getRatingCount())
                // Carried on every profile load so the driver app can show the
                // dues banner without a second round trip.
                .duesBalance(dues.getBalance())
                .duesThreshold(dues.getThreshold())
                .duesBlocked(dues.isBlocked())
                .build();
    }
}
