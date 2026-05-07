package com.backend.iniato.repo;

import com.backend.iniato.entity.Fare;
import com.backend.iniato.entity.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FareRepository extends JpaRepository<Fare, Long> {
    Optional<Fare> findByRide(Ride ride);
}
