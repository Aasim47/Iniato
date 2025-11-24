package com.backend.iniato.repo;


import com.backend.iniato.entity.DriverLocation;
import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DriverLocationRepository extends JpaRepository<DriverLocation, Long> {

    @Query("SELECT d FROM DriverLocation d WHERE distance(d.currentLocation, :pickup) < :radius")
    List<DriverLocation> findNearbyDrivers(Point pickup, double radius);


    Optional<DriverLocation> findByDriverId(Long id);
}

