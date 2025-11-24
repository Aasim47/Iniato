package com.backend.iniato.services;

import com.backend.iniato.dto.NearbyDriverDTO;
import com.backend.iniato.dto.RideMatchResponseDTO;
import com.backend.iniato.dto.RideRequestDTO;
import com.backend.iniato.dto.RideSummaryDTO;
import com.backend.iniato.entity.*;
import com.backend.iniato.repo.DriverLocationRepository;
import com.backend.iniato.repo.RideRepository;
import com.backend.iniato.repo.RideRequestRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RideMatchingService {

    private final RideRequestRepository rideRequestRepository;
    private final DriverLocationRepository driverLocationRepository;
    private final RideRepository rideRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory();

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

    /**
     * Find nearby shared rides OR available drivers near pickup location.
     */
    public RideMatchResponseDTO findSharedRideMatches(RideRequestDTO requestDTO) {
        // ✅ Step 1: Create pickup point from request
        Point pickup = geometryFactory.createPoint(
                new Coordinate(requestDTO.getPickupLng(), requestDTO.getPickupLat())
        );

        // ✅ Step 2: Define search radius (in degrees)
        double radius = 0.02; // roughly ≈ 2 km, adjust as needed

        // ✅ Step 3: Query nearby rides and drivers
        List<Ride> nearbyRides = rideRepository.findNearbyRides(pickup, radius);
        List<DriverLocation> drivers = driverLocationRepository.findNearbyDrivers(pickup, radius);

        // ✅ Step 4: Map rides to DTOs
        List<RideSummaryDTO> rideSummaries = nearbyRides.stream()
                .map(r -> RideSummaryDTO.builder()
                        .rideId(r.getId())
                        .pickupLocation(r.getPickupLocation())
                        .destination(r.getDestination())
                        .status(r.getStatus())
                        .requestedTime(r.getRequestedTime())
                        .driverName(r.getDriver() != null ? r.getDriver().getPhoneNumber() : "Unassigned")
                        .passengerNames(r.getPassengers().stream()
                                .map(User::getPhoneNumber)
                                .toList())
                        .build())
                .toList();

        // ✅ Step 5: Map drivers to DTOs
        List<NearbyDriverDTO> nearbyDriverDTOs = drivers.stream()
                .map(d -> new NearbyDriverDTO(
                        d.getDriver().getId(),
                        d.getCurrentLocation().getY(),
                        d.getCurrentLocation().getX(),
                        pickup.distance(d.getCurrentLocation()) * 111000 // convert degrees → meters
                ))
                .toList();

        // ✅ Step 6: Combine and return
        return RideMatchResponseDTO.builder()
                .matchingRides(rideSummaries)
                .nearbyDrivers(nearbyDriverDTOs)
                .build();
    }
}
