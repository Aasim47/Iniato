package com.backend.iniato.repo;

import com.backend.iniato.entity.Route;
import com.backend.iniato.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RouteRepository extends JpaRepository<Route, Long> {

    List<Route> findByStatus(String status);

    List<Route> findByDriver(User driver);

    List<Route> findByDriverAndStatus(User driver, String status);

    /**
     * Pessimistic-write lock on a single route, used when confirming a passenger
     * so concurrent accepts serialize on the seat count and cannot overbook.
     * Must be called inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Route r WHERE r.id = :id")
    Optional<Route> findByIdForUpdate(@Param("id") Long id);

    // Native PostGIS query: build a geography point from the route's origin
    // lat/lng columns and find ACTIVE routes whose origin is within :delta
    // metres of (:lat,:lng). NOTE: this was previously JPQL mis-flagged as
    // nativeQuery (SELECT r FROM Route ... against a non-existent "Route"
    // table, casting a scalar double to geography) and always 500'd.
    // Not @Cacheable: results carry live availableSeats, which must never be
    // served stale.
    //
    // available_seats > 0 is the single source of truth for "bookable". A full
    // vehicle drops out of rider search here and reappears automatically when
    // a passenger is dropped or leaves (see RideService.releaseSeat). There is
    // deliberately no FULL route status to keep in sync — a derived condition
    // cannot drift out of step with the seat count.
    @Query(value = "SELECT * FROM routes r WHERE r.status = 'ACTIVE' " +
           "AND r.available_seats > 0 " +
           "AND ST_DWithin(" +
           "  CAST(ST_SetSRID(ST_MakePoint(r.origin_lng, r.origin_lat), 4326) AS geography), " +
           "  CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography), " +
           "  :delta)", nativeQuery = true)
    List<Route> findActiveRoutesNear(@Param("lat") double lat,
                                     @Param("lng") double lng,
                                     @Param("delta") double delta);
}
