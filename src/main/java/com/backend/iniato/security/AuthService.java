package com.backend.iniato.security;

import com.backend.iniato.dto.AuthResponse;
import com.backend.iniato.dto.LoginRequest;
import com.backend.iniato.dto.RegisterRequest;
import com.backend.iniato.security.JwtService;
import com.backend.iniato.entity.User;
import com.backend.iniato.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor

public class AuthService {
@Autowired
    private  UserRepository userRepository;
    @Autowired
    private  PasswordEncoder passwordEncoder;
    @Autowired
    private  JwtService jwtService;
    @Autowired
    private  AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        // 1. Check if user already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already in use");
        }

        // 2. Create new user
        var user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword())) // Encrypt password
                .roles(request.getRoles())
                .build();

        // 3. Save user to DB
        userRepository.save(user);

        // 4. Generate JWT
        var jwtToken = jwtService.generateToken(user);

        // 5. Return AuthResponse
        return AuthResponse.builder()
                .token(jwtToken)
                .email(user.getEmail())
                .roles(user.getRoles())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        // 1. Authenticate user
        // This will throw AuthenticationException if credentials are bad
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // 2. If authentication is successful, find user
        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // 3. Generate JWT
        var jwtToken = jwtService.generateToken(user);

        // 4. Return AuthResponse
        return AuthResponse.builder()
                .token(jwtToken)
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }
}