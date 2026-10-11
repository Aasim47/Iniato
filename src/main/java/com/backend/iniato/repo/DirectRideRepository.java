package com.backend.iniato.repo;

import com.backend.iniato.entity.DirectRide;
import com.backend.iniato.entity.User;
import com.backend.iniato.enums.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DirectRideRepository extends JpaRepository<DirectRide, Long> {

    List<DirectRide> findByPassenger(User passenger);

    List<DirectRide> findByDriver(User driver);

    List<DirectRide> findByStatus(RideStatus status);

    Optional<DirectRide> findByIdAndPassenger(Long id, User passenger);

    Optional<DirectRide> findByIdAndDriver(Long id, User driver);

    @Query("SELECT d FROM DirectRide d WHERE d.status = :status AND d.driver IS NULL")
    List<DirectRide> findAvailableRides(@Param("status") RideStatus status);

    @Query(value = "SELECT * FROM direct_rides d WHERE d.status = :#{#status.name()} AND d.driver_user_id IS NULL AND ST_DistanceSphere(d.pickup_location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)) <= :radius", nativeQuery = true)
    List<DirectRide> findAvailableRidesWithinRadius(@Param("status") RideStatus status, @Param("lat") double lat, @Param("lng") double lng, @Param("radius") double radius);

    @Query("SELECT d FROM DirectRide d WHERE d.status = :status AND d.driver IS NOT NULL")
    List<DirectRide> findAcceptedRides(@Param("status") RideStatus status);

    @Query("SELECT d FROM DirectRide d WHERE d.status = :status AND d.passenger IS NOT NULL")
    List<DirectRide> findCompletedRides(@Param("status") RideStatus status);
}