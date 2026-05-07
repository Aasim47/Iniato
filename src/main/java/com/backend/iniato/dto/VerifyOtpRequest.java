package com.backend.iniato.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class VerifyOtpRequest {
    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotBlank(message = "OTP is required")
    @Size(min = 4, max = 8, message = "OTP must be 4-8 digits")
    private String otp;

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }
}
