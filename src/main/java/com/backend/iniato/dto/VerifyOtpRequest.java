package com.backend.iniato.dto;


import lombok.Data;

@Data
public class VerifyOtpRequest {

    private String phoneNumber;
    private String otp;
}
