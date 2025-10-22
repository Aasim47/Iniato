package com.backend.iniato.services;


import com.backend.iniato.dto.NearbyDriverDTO;
import com.backend.iniato.dto.RideRequestDTO;
import com.backend.iniato.entity.*;
import com.backend.iniato.repo.DriverLocationRepository;
import com.backend.iniato.repo.RideRequestRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RideMatchingService {

    private final RideRequestRepository rideRequestRepository;
    private final DriverLocationRepository driverLocationRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    public List<NearbyDriverDTO> findNearbyDrivers(RideRequestDTO requestDTO) {
        Point pickup = geometryFactory.createPoint(new Coordinate(requestDTO.getPickupLng(), requestDTO.getPickupLat()));
        double radius = 500; // meters

        List<DriverLocation> drivers = driverLocationRepository.findNearbyDrivers(pickup, radius);

        return drivers.stream()
                .map(d -> new NearbyDriverDTO(
                        d.getDriver().getUser_id(),
                        d.getCurrentLocation().getY(),
                        d.getCurrentLocation().getX(),
                        pickup.distance(d.getCurrentLocation()) * 111000 // degrees → meters approx
                ))
                .collect(Collectors.toList());
    }

    public RideRequest saveRideRequest(RideRequestDTO requestDTO, User passenger) {
        Point pickup = geometryFactory.createPoint(new Coordinate(requestDTO.getPickupLng(), requestDTO.getPickupLat()));
        Point destination = geometryFactory.createPoint(new Coordinate(requestDTO.getDestLng(), requestDTO.getDestLat()));

        RideRequest rideRequest = RideRequest.builder()
                .passenger(passenger)
                .pickupLocation(pickup)
                .destinationLocation(destination)
                .pickupTime(java.time.LocalDateTime.parse(requestDTO.getPickupTime()))
                .status("REQUESTED")
                .build();

        return rideRequestRepository.save(rideRequest);
    }
}

