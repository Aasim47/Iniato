package com.backend.iniato.services;

import com.backend.iniato.entity.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads the authenticated principal for {@code JwtAuthenticationFilter}.
 *
 * <p>This runs on <em>every</em> authenticated request, which makes it the
 * single hottest database path in the app. It now delegates to
 * {@link UserLookupService}, so a warm cache serves it without touching
 * Postgres at all — and on Neon's free plan a query not made is compute not
 * billed.
 *
 * <p>The returned User is detached and its roles are already populated, so
 * nothing here needs {@code Hibernate.initialize} or an open session. That is
 * what lets {@code spring.jpa.open-in-view=false} be safe on this path.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserLookupService userLookupService;

    public CustomUserDetailsService(UserLookupService userLookupService) {
        this.userLookupService = userLookupService;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        CachedUser cached = userLookupService.findCached(identifier);
        if (cached == null) {
            throw new UsernameNotFoundException(
                    "User not found with email or phone: " + identifier);
        }
        User user = cached.toUser();
        // Touch authorities here so any failure surfaces during authentication
        // rather than deep inside a @PreAuthorize check.
        user.getAuthorities();
        return user;
    }
}
