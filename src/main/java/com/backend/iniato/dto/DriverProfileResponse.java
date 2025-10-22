package com.backend.iniato.dto;

import com.backend.iniato.enums.DriverStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DriverProfileResponse {
    private String email;
    private String fullName;
    private String phoneNumber;
    private String vehicleRegistration;
    private String licenseNumber;
    private DriverStatus status;
}
