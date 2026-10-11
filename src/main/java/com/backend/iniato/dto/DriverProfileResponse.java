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
    /** BIKE, AUTO or CAB — see {@link com.backend.iniato.enums.VehicleType}. */
    private String vehicleType;
    /** Passenger capacity implied by {@link #vehicleType}. Read-only. */
    private Integer seatCapacity;
    private DriverStatus status;
    private Double averageRating;
    private Integer ratingCount;

    /** Platform fees the driver has collected in cash and still owes Iniato. */
    private java.math.BigDecimal duesBalance;
    /** Balance at which the driver is blocked from taking new work. */
    private java.math.BigDecimal duesThreshold;
    /** {@code duesBalance >= duesThreshold} — cannot go online or accept rides. */
    private Boolean duesBlocked;
}
