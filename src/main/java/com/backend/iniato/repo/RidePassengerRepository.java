package com.backend.iniato.repo;

import com.backend.iniato.entity.Ride;
import com.backend.iniato.entity.RidePassenger;
import com.backend.iniato.entity.User;
import com.backend.iniato.enums.RidePassengerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RidePassengerRepository extends JpaRepository<RidePassenger, Long> {

    List<RidePassenger> findByPassenger(User passenger);

    List<RidePassenger> findByRide(Ride ride);

    Optional<RidePassenger> findByRideAndPassenger(Ride ride, User passenger);

    List<RidePassenger> findByRideAndStatus(Ride ride, RidePassengerStatus status);

    List<RidePassenger> findByPassengerAndStatus(User passenger, RidePassengerStatus status);

    boolean existsByRideAndPassenger(Ride ride, User passenger);
}
