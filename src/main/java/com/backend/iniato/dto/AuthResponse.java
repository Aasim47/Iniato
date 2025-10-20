package com.backend.iniato.dto;


import com.backend.iniato.entity.Role;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder

@Data
public class AuthResponse {
    private String token;
    private String email;
    private List<String> roles;

}