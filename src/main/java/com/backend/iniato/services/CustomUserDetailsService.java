package com.backend.iniato.services;


import com.backend.iniato.entity.User;
import com.backend.iniato.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        try {
            User user = userRepository.findByEmail(email.trim().toLowerCase())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

            userRepository.findByEmail("skaasim47@gmail.com")
                    .ifPresentOrElse(
                            u -> System.out.println("Found user: " + u.getEmail()),
                            () -> System.out.println("No user found")
                    );

            Hibernate.initialize(user.getRoles());

            return user;

        } catch (Exception e) {
            throw new UsernameNotFoundException("Error loading user: " + email, e);
        }
    }
}