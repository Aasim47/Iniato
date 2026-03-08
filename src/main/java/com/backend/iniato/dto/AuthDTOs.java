package com.backend.iniato.dto;

public class SendOtpRequest {
    public String phone;
}

public class VerifyOtpRequest {
    public String phone;
    public String otp;
}

public class RefreshTokenRequest {
    public String refreshToken;
}

