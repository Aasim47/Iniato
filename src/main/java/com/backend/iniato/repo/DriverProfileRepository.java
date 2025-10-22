package com.backend.iniato.repo;

import com.backend.iniato.entity.DriverProfile;
import com.backend.iniato.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, Long> {
    Optional<DriverProfile> findByUser(User user);
}