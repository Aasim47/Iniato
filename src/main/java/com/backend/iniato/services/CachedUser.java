package com.backend.iniato.services;

import com.backend.iniato.entity.Role;
import com.backend.iniato.entity.User;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A flat, serializable snapshot of a {@link User}, and the thing that actually
 * lives in the {@code users} cache.
 *
 * <p>The entity itself is deliberately <b>not</b> cached. Two things make it
 * unsafe to put in Redis:
 * <ul>
 *   <li>{@code Role.users} is a lazy back-reference to {@code User}, so
 *       serializing a Role either recurses or trips on an uninitialised
 *       proxy.</li>
 *   <li>{@code User.roles} is a Hibernate {@code PersistentSet}. Jackson's
 *       default typing writes that concrete class name and then cannot
 *       reconstruct it on read.</li>
 * </ul>
 *
 * <p>So the snapshot carries only plain values, and {@link #toUser()} rebuilds
 * a detached {@code User} from them. Detached is the same thing the JWT filter
 * has always put in the security context — {@code @AuthenticationPrincipal User}
 * was already handing services a detached entity — so this changes nothing
 * about how callers may use it.
 *
 * <p><b>Read-only.</b> A rebuilt User is safe to read, to pass to derived
 * queries (which use its id), and to assign to a {@code @ManyToOne}. It must
 * never be handed to {@code save()}: its rebuilt Roles are detached too, and
 * merging them risks writing the role table. Write paths re-read a managed
 * entity first — see {@code UserLookupService.loadManaged}.
 */
public record CachedUser(
        Long id,
        String email,
        String password,
        String phoneNumber,
        String userType,
        List<CachedRole> roles) {

    /** Role id and name — everything {@code getAuthorities()} needs. */
    public record CachedRole(Long roleId, String roleName) {}

    public static CachedUser from(User user) {
        List<CachedRole> roleSnapshot = new ArrayList<>();
        if (user.getRoles() != null) {
            user.getRoles().forEach(r ->
                    roleSnapshot.add(new CachedRole(r.getRole_id(), r.getRoleName())));
        }
        return new CachedUser(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getPhoneNumber(),
                user.getUserType() != null ? user.getUserType().name() : null,
                roleSnapshot);
    }

    /** Rebuild a detached User. Read-only — never pass the result to save(). */
    public User toUser() {
        Set<Role> rebuilt = new LinkedHashSet<>();
        if (roles != null) {
            for (CachedRole r : roles) {
                Role role = new Role();
                role.setRole_id(r.roleId());
                role.setRoleName(r.roleName());
                rebuilt.add(role);
            }
        }
        return User.builder()
                .id(id)
                .email(email)
                .password(password)
                .phoneNumber(phoneNumber)
                .userType(userType != null ? User.UserType.valueOf(userType) : null)
                .roles(rebuilt)
                .build();
    }
}
