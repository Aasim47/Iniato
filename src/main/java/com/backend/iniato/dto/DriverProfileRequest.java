package com.backend.iniato.dto;

import lombok.Data;

@Data
public class DriverProfileRequest {
    private String fullName;
    private String phoneNumber;
    private String vehicleRegistration;
    private String licenseNumber;
}