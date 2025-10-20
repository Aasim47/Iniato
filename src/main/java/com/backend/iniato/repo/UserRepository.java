package com.backend.iniato.repo;

import com.backend.iniato.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Spring Data JPA will auto-implement this method
    Optional<User> findByEmail(String email);
}