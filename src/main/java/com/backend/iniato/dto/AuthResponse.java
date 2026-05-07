package com.backend.iniato.dto;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class AuthResponse {
    private String token;
    private String refreshToken;
    private String phoneNumber;
    private String userType;
}