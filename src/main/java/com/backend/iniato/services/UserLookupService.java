package com.backend.iniato.services;

import com.backend.iniato.config.CacheConfig;
import com.backend.iniato.entity.User;
import com.backend.iniato.repo.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place a User is looked up by identifier, and the only place that
 * lookup is cached.
 *
 * <p><b>Why this exists.</b> Six classes had their own private
 * {@code getCurrentUser()}, each doing
 * {@code findByEmail(id).or(() -> findByPhoneNumber(id))}. Because almost every
 * account in this app is phone-based, the first finder missed and the second
 * ran — two queries, every time. Worse, the JWT filter had already performed
 * the identical lookup microseconds earlier to build the security context, so a
 * single authenticated request paid for the same user four times.
 *
 * <p>Now: one OR'd query instead of two, cached for 10 minutes, shared by the
 * filter and every service. A warm request costs zero user queries.
 *
 * <p><b>Staleness.</b> The cache is evicted explicitly by {@link #evict} on
 * every path that writes a User, under both keys the user can be found by. The
 * TTL is a backstop for an eviction that was missed, not the primary mechanism.
 */
@Service
public class UserLookupService {

    private final UserRepository userRepository;

    public UserLookupService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Find a user by email or phone, whichever the identifier turns out to be.
     *
     * <p>Cached on the raw identifier. The same user reached by email and by
     * phone occupies two entries; {@link #evict} clears both.
     *
     * <p>Returns a detached, read-only User — see {@link CachedUser}.
     */
    @Cacheable(value = CacheConfig.USERS_CACHE, key = "'id:' + #identifier", sync = true)
    @Transactional(readOnly = true)
    public CachedUser findCached(String identifier) {
        if (identifier == null || identifier.isBlank()) return null;
        String normalized = identifier.trim();

        // One query where there used to be two. Email is stored lowercase;
        // phone numbers are not, so the raw value is matched as well.
        return userRepository
                .findByEmailOrPhoneNumber(normalized.toLowerCase(), normalized)
                .map(CachedUser::from)
                .orElse(null);
    }

    /** The authenticated user, or null when nothing is authenticated. */
    public User currentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;

        // The filter already put a fully-built User in the context. Reuse it
        // rather than going back to the cache, let alone the database.
        if (auth.getPrincipal() instanceof User principal && principal.getId() != null) {
            return principal;
        }
        CachedUser cached = findCached(auth.getName());
        return cached != null ? cached.toUser() : null;
    }

    /**
     * The authenticated user.
     *
     * @throws IllegalStateException when there is none — the same failure the
     *         per-service {@code getCurrentUser()} methods raised, now in one place
     */
    public User currentUser() {
        User user = currentUserOrNull();
        if (user == null) throw new IllegalStateException("User not found");
        return user;
    }

    /**
     * A <b>managed</b> User for the current transaction, for write paths.
     *
     * <p>{@link #currentUser()} may hand back a detached instance rebuilt from
     * cache; passing that to {@code save()} would merge detached Roles along
     * with it. Callers that intend to modify the user go through here.
     */
    @Transactional(readOnly = true)
    public User loadManaged(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));
    }

    /**
     * Drop a user from the cache under every identifier they answer to.
     *
     * <p>Call after any write that changes a User's identity or roles. Phone
     * number is the one mutable identifier ({@code PassengerProfileService}
     * changes it), so the <em>old</em> value must be evicted too — evict before
     * mutating, or pass the previous identifier explicitly.
     */
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.USERS_CACHE, key = "'id:' + #email", condition = "#email != null"),
            @CacheEvict(value = CacheConfig.USERS_CACHE, key = "'id:' + #phoneNumber", condition = "#phoneNumber != null")
    })
    public void evict(String email, String phoneNumber) {
        // Annotation-driven; nothing to do here.
    }

    /** Evict every identifier this user currently answers to. */
    public void evict(User user) {
        if (user == null) return;
        evict(user.getEmail(), user.getPhoneNumber());
    }

    /** Clear the whole user cache — for a role change or a bulk fix-up. */
    @CacheEvict(value = CacheConfig.USERS_CACHE, allEntries = true)
    public void evictAll() {
        // Annotation-driven.
    }
}
