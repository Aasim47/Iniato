package com.backend.iniato.services;

import com.backend.iniato.dto.NearbyDriverDTO;
import com.backend.iniato.dto.RideMatchResponseDTO;
import com.backend.iniato.dto.RideRequestDTO;
import com.backend.iniato.dto.RideSummaryDTO;
import com.backend.iniato.entity.*;
import com.backend.iniato.enums.RidePassengerStatus;
import com.backend.iniato.enums.RideStatus;
import com.backend.iniato.repo.DriverLocationRepository;
import com.backend.iniato.repo.RideRepository;
import com.backend.iniato.repo.RideRequestRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RideMatchingService {

    private final RideRequestRepository rideRequestRepository;
    private final DriverLocationRepository driverLocationRepository;
    private final RideRepository rideRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Autowired
    public RideMatchingService(RideRequestRepository rideRequestRepository, DriverLocationRepository driverLocationRepository, RideRepository rideRepository) {
        this.rideRequestRepository = rideRequestRepository;
        this.driverLocationRepository = driverLocationRepository;
        this.rideRepository = rideRepository;
    }

    public RideRequest saveRideRequest(RideRequestDTO requestDTO, User passenger) {
        Point pickup = geometryFactory.createPoint(
                new Coordinate(requestDTO.getPickupLng(), requestDTO.getPickupLat()));
        Point destination = geometryFactory.createPoint(
                new Coordinate(requestDTO.getDestLng(), requestDTO.getDestLat()));

        RideRequest rideRequest = RideRequest.builder()
                .passenger(passenger)
                .pickupLocation(pickup)
                .destinationLocation(destination)
                .pickupTime(java.time.LocalDateTime.parse(requestDTO.getPickupTime()))
                .status("REQUESTED")
                .build();

        return rideRequestRepository.save(rideRequest);
    }

    /**
     * Find active POOL_FORMING rides and nearby drivers for a given pickup location.
     */
    public RideMatchResponseDTO findSharedRideMatches(RideRequestDTO requestDTO) {
        Point pickup = geometryFactory.createPoint(
                new Coordinate(requestDTO.getPickupLng(), requestDTO.getPickupLat()));

        double radius = 0.02; // ~2 km in degrees

        List<Ride> nearbyRides = rideRepository.findByStatus(RideStatus.POOL_FORMING);
        List<DriverLocation> drivers = driverLocationRepository.findNearbyDrivers(pickup, radius);

        List<RideSummaryDTO> rideSummaries = nearbyRides.stream()
                .map(r -> RideSummaryDTO.builder()
                        .rideId(r.getId())
                        .pickupLocation(r.getPickupLocation())
                        .destination(r.getDestination())
                        .status(r.getStatus())
                        .requestedTime(r.getRequestedTime())
                        .driverName(r.getDriver() != null ? r.getDriver().getPhoneNumber() : "Unassigned")
                        .passengerNames(r.getPassengers().stream()
                                .filter(rp -> rp.getStatus() == RidePassengerStatus.CONFIRMED)
                                .map(rp -> rp.getPassenger().getPhoneNumber())
                                .toList())
                        .build())
                .toList();

        List<NearbyDriverDTO> nearbyDriverDTOs = drivers.stream()
                .filter(d -> d.getDriver() != null)
                .map(d -> new NearbyDriverDTO(
                        d.getDriver().getId(),
                        d.getCurrentLocation().getY(),
                        d.getCurrentLocation().getX(),
                        pickup.distance(d.getCurrentLocation()) * 111000
                ))
                .toList();

        return RideMatchResponseDTO.builder()
                .matchingRides(rideSummaries)
                .nearbyDrivers(nearbyDriverDTOs)
                .build();
    }
}
