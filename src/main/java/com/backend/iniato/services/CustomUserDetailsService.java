package com.backend.iniato.services;

import com.backend.iniato.entity.User;
import com.backend.iniato.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        String normalized = identifier.trim().toLowerCase();

        User user = userRepository.findByEmail(normalized)
                .or(() -> userRepository.findByPhoneNumber(normalized))
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found with email or phone: " + identifier));

        Hibernate.initialize(user.getAuthorities());

        return user;
    }
}
