package com.backend.iniato.repo;


import com.backend.iniato.entity.Ride;
import com.backend.iniato.entity.User;
import com.backend.iniato.enums.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RideRepository extends JpaRepository<Ride, Long> {
    List<Ride> findByPassenger(User passenger);
    List<Ride> findByDriver(User driver);
    List<Ride> findByStatus(RideStatus status);
}
