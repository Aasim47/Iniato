package com.backend.iniato.repo;


import com.backend.iniato.entity.PassengerProfile;
import com.backend.iniato.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PassengerProfileRepository extends JpaRepository<PassengerProfile, Long> {
    Optional<PassengerProfile> findByUser(User user);
}