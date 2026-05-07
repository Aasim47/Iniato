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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Route r WHERE r.id = :id")
    Optional<Route> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT r FROM Route r WHERE r.status = 'ACTIVE' " +
           "AND ABS(r.originLat - :lat) < :delta " +
           "AND ABS(r.originLng - :lng) < :delta")
    List<Route> findActiveRoutesNear(@Param("lat") double lat,
                                     @Param("lng") double lng,
                                     @Param("delta") double delta);
}
