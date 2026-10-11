package com.backend.iniato.repo;

import com.backend.iniato.entity.Ride;
import com.backend.iniato.entity.Route;
import com.backend.iniato.entity.User;
import com.backend.iniato.enums.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RideRepository extends JpaRepository<Ride, Long> {

    List<Ride> findByDriver(User driver);

    List<Ride> findByStatus(RideStatus status);

    List<Ride> findByStatusIn(List<RideStatus> statuses);

    Optional<Ride> findByRoute(Route route);

    /** Batch-fetch rides for a set of routes to avoid per-route N+1 lookups. */
    List<Ride> findByRouteIn(List<Route> routes);

    @Query("SELECT r FROM Ride r WHERE r.status = 'POOL_FORMING' " +
           "AND r.pickupLocation = :pickup AND r.destination = :destination")
    List<Ride> findActiveRidesByRoute(@Param("pickup") String pickup,
                                      @Param("destination") String destination);
}
