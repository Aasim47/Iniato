package com.backend.iniato.dto;

import com.backend.iniato.enums.DriverStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DriverProfileResponse {
    private Long userId;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String vehicleRegistration;
    private String licenseNumber;
    /** AUTO_RICKSHAW, BIKE, CAR, MINI_TRUCK */
    private String vehicleType;
    private DriverStatus status;
    private Double averageRating;
    private Integer ratingCount;
}
