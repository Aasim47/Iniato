package com.backend.iniato.repo;

import com.backend.iniato.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Caching lives in UserLookupService, not here. Annotating these finders
    // directly was the old mistake: it cached the JPA entity (unserializable —
    // lazy Role back-reference, Hibernate PersistentSet) and had no eviction,
    // so a phone-number change served a stale user until restart. The service
    // caches a flat snapshot instead and evicts on every write path.
    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    /**
     * Resolve a login identifier in one round trip.
     *
     * <p>Callers used to chain {@code findByEmail(id).or(() -> findByPhoneNumber(id))}.
     * Nearly every account here is phone-based, so the email lookup missed and
     * both queries ran — on every authenticated request. This collapses them.
     *
     * <p>Both parameters are usually the same identifier: email is stored
     * lowercase, phone numbers are not, so each is matched in its own form.
     * {@code roles} is fetched eagerly in the same statement, because the
     * caller always reads authorities and would otherwise trigger a second
     * select. {@code LEFT JOIN} keeps users who have no roles.
     */
    @Query("""
            SELECT DISTINCT u FROM User u
            LEFT JOIN FETCH u.roles
            WHERE u.email = :email OR u.phoneNumber = :phoneNumber
            """)
    Optional<User> findByEmailOrPhoneNumber(@Param("email") String email,
                                            @Param("phoneNumber") String phoneNumber);
}
