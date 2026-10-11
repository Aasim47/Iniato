package com.backend.iniato.services;

import com.backend.iniato.entity.Role;
import com.backend.iniato.entity.User;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the snapshot that sits in the {@code users} Redis cache.
 *
 * <p>The cache is on the hot path of every authenticated request, so a
 * serialization fault here would not be a cache miss — it would be a failed
 * login. These tests pin the two things that can break it: the fields survive
 * a JSON round trip, and the rebuilt User still reports the right authorities.
 */
class CachedUserTest {

    /** Mirrors the serializer configured in {@code CacheConfig}. */
    private static ObjectMapper cacheMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return mapper;
    }

    private static User driverWithRoles() {
        Set<Role> roles = new LinkedHashSet<>();
        Role driver = new Role();
        driver.setRole_id(1L);
        driver.setRoleName("ROLE_DRIVER");
        roles.add(driver);

        return User.builder()
                .id(42L)
                .email("driver@example.com")
                .password("$2a$10$hashed")
                .phoneNumber("9876543210")
                .userType(User.UserType.DRIVER)
                .roles(roles)
                .build();
    }

    @Test
    @DisplayName("snapshot survives the Redis JSON round trip")
    void roundTripsThroughJson() throws Exception {
        CachedUser original = CachedUser.from(driverWithRoles());

        ObjectMapper mapper = cacheMapper();
        String json = mapper.writeValueAsString(original);
        CachedUser restored = mapper.readValue(json, CachedUser.class);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.roles()).extracting(CachedUser.CachedRole::roleName)
                .containsExactly("ROLE_DRIVER");
    }

    @Test
    @DisplayName("rebuilt user keeps its identity and authorities")
    void rebuildsUsableUser() {
        User rebuilt = CachedUser.from(driverWithRoles()).toUser();

        assertThat(rebuilt.getId()).isEqualTo(42L);
        assertThat(rebuilt.getEmail()).isEqualTo("driver@example.com");
        assertThat(rebuilt.getPhoneNumber()).isEqualTo("9876543210");
        assertThat(rebuilt.getUserType()).isEqualTo(User.UserType.DRIVER);

        // Authorities drive every @PreAuthorize check in the app: the role plus
        // the UserType, which is what "hasAuthority('DRIVER')" matches on.
        assertThat(rebuilt.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_DRIVER", "DRIVER");
    }

    @Test
    @DisplayName("password is carried, so JWT validation still works")
    void keepsPasswordForAuthentication() {
        // UserDetails contract: Spring Security compares this during
        // authentication. Dropping it from the snapshot would break login in a
        // way no cache test would otherwise catch.
        assertThat(CachedUser.from(driverWithRoles()).toUser().getPassword())
                .isEqualTo("$2a$10$hashed");
    }

    @Test
    @DisplayName("phone-only user (no email, no roles) round trips")
    void handlesPhoneOnlyUser() throws Exception {
        // The common shape in this app: OTP registration sets no email and
        // assigns no Role rows, leaving authorities to come from UserType alone.
        User phoneOnly = User.builder()
                .id(7L)
                .phoneNumber("9000000001")
                .userType(User.UserType.PASSENGER)
                .build();

        ObjectMapper mapper = cacheMapper();
        CachedUser restored = mapper.readValue(
                mapper.writeValueAsString(CachedUser.from(phoneOnly)), CachedUser.class);

        User rebuilt = restored.toUser();
        assertThat(rebuilt.getEmail()).isNull();
        assertThat(rebuilt.getUsername()).isEqualTo("9000000001");
        assertThat(rebuilt.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("PASSENGER");
    }
}
