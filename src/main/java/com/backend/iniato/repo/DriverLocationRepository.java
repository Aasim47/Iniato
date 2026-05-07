package com.backend.iniato.repo;


import com.backend.iniato.entity.DriverLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DriverLocationRepository extends JpaRepository<DriverLocation, Long> {

    @Query(value = "SELECT * FROM driver_locations d " +
            "WHERE d.online = true " +
            "AND ST_DWithin(" +
            "  CAST(d.current_location AS geography), " +
            "  CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography), " +
            "  :radius)",
            nativeQuery = true)
    List<DriverLocation> findNearbyDrivers(@Param("lng") double lng,
                                           @Param("lat") double lat,
                                           @Param("radius") double radius);


    Optional<DriverLocation> findByDriverId(Long id);
}

