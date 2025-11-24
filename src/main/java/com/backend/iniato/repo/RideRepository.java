package com.backend.iniato.repo;


import com.backend.iniato.entity.Ride;
import com.backend.iniato.entity.User;
import com.backend.iniato.enums.RideStatus;
import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RideRepository extends JpaRepository<Ride, Long> {
    List<Ride> findByPassenger(User passenger);
    List<Ride> findByDriver(User driver);
    List<Ride> findByStatus(RideStatus status);

    @Query("SELECT r FROM Ride r WHERE r.status = 'POOL_FORMING' " +
            "AND r.pickupLocation = :pickup AND r.destination = :destination")
    List<Ride> findActiveRidesByRoute(String pickup, String destination);

    @Query("SELECT r FROM Ride r WHERE distance(r.pickupLat, r.pickupLng, :pickup.y, :pickup.x) < 1.0")
    List<Ride> findNearbyRides(Point pickup, double radius);

}
