package com.backend.iniato.dto;


import com.backend.iniato.entity.Role;
import lombok.Builder;

@Builder


public class AuthResponse {
    private String token;
    private String email;
    private Role roles;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Role getRoles() {
        return roles;
    }

    public void setRoles(Role roles) {
        this.roles = roles;
    }
}