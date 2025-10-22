package com.backend.iniato.dto;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PassengerProfileResponse {
    private String email;
    private String fullName;
    private String phoneNumber;
    private String autoRegistrationNumber;
    private String licenseNumber;
    private boolean onlineStatus;
}