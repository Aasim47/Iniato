package com.backend.iniato.repo;

import com.backend.iniato.entity.Ride;
import com.backend.iniato.entity.RideRequest;
import com.backend.iniato.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RideRequestRepository extends JpaRepository<RideRequest, Long> {

    List<RideRequest> findByPassenger(User passenger);

    List<RideRequest> findByMatchedRideAndStatus(Ride ride, String status);
}
